import { tool } from "@opencode-ai/plugin"
import { promises as fs } from "node:fs"
import { execFile } from "node:child_process"

const PURE_PACKAGES = [
  "core",
  "geo",
  "camera",
  "layers",
  "field",
  "measure",
  "goto",
  "track",
  "offline",
  "tactical",
]

const FORBIDDEN_IMPORTS = [
  { pattern: /^import\s+org\.maplibre\./, label: "org.maplibre.* (MapLibre)" },
  { pattern: /^import\s+androidx\.compose/, label: "androidx.compose.* (Compose)" },
  { pattern: /^import\s+android\./, label: "android.* (Android framework)" },
  {
    pattern: /^import\s+androidx\.(?!compose)/,
    label: "androidx.* outside Compose",
  },
  {
    pattern: /^import\s+kotlinx\.coroutines\.android/,
    label: "kotlinx.coroutines.android",
  },
]

function androidDir(worktree) {
  return worktree + "\\apps\\atlas-android"
}

function run(cmd, args, cwd, timeoutMs) {
  const limit = timeoutMs || 900000
  return new Promise((resolve) => {
    const child = execFile(
      cmd,
      args,
      { cwd: cwd, timeout: limit, maxBuffer: 64 * 1024 * 1024, windowsHide: true },
      (error, stdout, stderr) => {
        let code = 0
        if (error) {
          code = typeof error.code === "number" ? error.code : 1
        }
        resolve({
          code: code,
          stdout: (stdout ? stdout.toString() : ""),
          stderr: (stderr ? stderr.toString() : ""),
        })
      }
    )
    child.on("error", () => {
      resolve({ code: 127, stdout: "", stderr: "spawn failed" })
    })
  })
}

async function kotlinFiles(root) {
  const out = []
  async function walk(dir) {
    let entries
    try {
      entries = await fs.readdir(dir, { withFileTypes: true })
    } catch {
      return
    }
    for (const entry of entries) {
      const full = dir + "\\" + entry.name
      if (entry.isDirectory()) {
        await walk(full)
      } else if (entry.name.endsWith(".kt")) {
        out.push(full)
      }
    }
  }
  await walk(root)
  return out
}

function relative(worktree, file) {
  return file.startsWith(worktree) ? file.slice(worktree.length + 1) : file
}

const tools = {
  atlas_purity_scan: tool({
    description:
      "Audit the Atlas engine/adapters boundary (RULES.md 2.1). Scans the pure-logic " +
      "packages (core, geo, camera, layers, field, measure, goto, track, offline, tactical) " +
      "for forbidden MapLibre, Compose, or Android imports and reports each violation with " +
      "file:line. Use after editing anything in a pure package, and before claiming a gate is green.",
    args: {
      package: tool.schema
        .string()
        .optional()
        .describe("Limit the scan to one pure package, e.g. geo. Omit to scan all of them."),
    },
    async execute(args, ctx) {
      const srcRoot =
        androidDir(ctx.worktree) +
        "\\app\\src\\main\\java\\com\\sovereignatlas\\atlas"
      const packages = args.package ? [args.package] : PURE_PACKAGES
      if (args.package && !PURE_PACKAGES.includes(args.package)) {
        return (
          "Unknown pure package '" + args.package + "'. Valid: " + PURE_PACKAGES.join(", ")
        )
      }

      const violations = []
      let scanned = 0

      for (const pkg of packages) {
        const files = await kotlinFiles(srcRoot + "\\" + pkg)
        scanned += files.length
        for (const file of files) {
          const text = await fs.readFile(file, "utf8")
          const lines = text.split(/\r?\n/)
          for (let i = 0; i < lines.length; i++) {
            const line = lines[i]
            if (!line.trimStart().startsWith("import ")) continue
            for (const rule of FORBIDDEN_IMPORTS) {
              if (rule.pattern.test(line)) {
                violations.push(
                  relative(ctx.worktree, file) + ":" + (i + 1) + "  " + rule.label
                )
              }
            }
          }
        }
      }

      if (violations.length === 0) {
        return (
          "PURITY CLEAN - " + scanned + " Kotlin files scanned across " +
          packages.length + " package(s): " + packages.join(", ")
        )
      }
      return [
        "PURITY VIOLATIONS - " + violations.length + " found in " + scanned + " files scanned.",
        "Pure packages must not import MapLibre, Compose, or Android. Move the logic down, or",
        "keep the platform type behind an abstraction.",
        "",
      ].concat(violations).join("\n")
    },
  }),

  atlas_gates: tool({
    description:
      "Run the Sovereign Atlas host verification gates and report a compact, filtered result. " +
      "Runs both flavor unit-test tasks, counts tests from the JUnit XML, and reports failures " +
      "with file:line. Use for the host gate after any change. Does not assemble APKs or touch " +
      "a device unless include_assemble is set (RULES.md 1.6).",
    args: {
      task: tool.schema
        .string()
        .optional()
        .describe("Gradle task to run. Defaults to both flavor unit-test tasks."),
      include_assemble: tool.schema
        .boolean()
        .optional()
        .describe("Also run both debug assemble tasks. Only on explicit ask (RULES.md 1.6)."),
    },
    async execute(args, ctx) {
      const dir = androidDir(ctx.worktree)
      const env = Object.assign({}, process.env, {
        JAVA_HOME: "C:\\Users\\612co\\.jdks\\jbr-21.0.11",
        ANDROID_HOME: "C:\\android",
      })
      const tasks = args.task
        ? [args.task]
        : [":app:testPlayDebugUnitTest", ":app:testEnterpriseDebugUnitTest"]

      const result = await new Promise((resolve) => {
        const child = execFile(
          "gradlew.bat",
          tasks.concat(["--console=plain"]),
          {
            cwd: dir,
            env: env,
            timeout: 3600000,
            maxBuffer: 128 * 1024 * 1024,
            windowsHide: true,
          },
          (error, stdout, stderr) => {
            let code = 0
            if (error) code = typeof error.code === "number" ? error.code : 1
            resolve({
              code: code,
              out: (stdout ? stdout.toString() : ""),
              err: (stderr ? stderr.toString() : ""),
            })
          }
        )
        child.on("error", () => {
          resolve({ code: 127, out: "", err: "gradlew.bat spawn failed" })
        })
      })

      const lines = []
      lines.push("exit=" + result.code + "  tasks=" + tasks.join(" "))
      lines.push(/BUILD SUCCESSFUL/.test(result.out) ? "BUILD SUCCESSFUL" : "BUILD FAILED")

      const resultDirs = ["testPlayDebugUnitTest", "testEnterpriseDebugUnitTest"]
      let grandTotal = 0
      let grandFailures = 0
      for (const name of resultDirs) {
        const xmlDir = dir + "\\app\\build\\test-results\\" + name
        let files = []
        try {
          files = (await fs.readdir(xmlDir)).filter((f) => f.endsWith(".xml"))
        } catch {
          continue
        }
        let tests = 0
        let bad = 0
        for (const file of files) {
          const xml = await fs.readFile(xmlDir + "\\" + file, "utf8")
          const suite = (xml.match(/<testsuite\b[^>]*>/) || [""])[0]
          const readNum = (attr) => {
            const m = suite.match(new RegExp("\\b" + attr + '="(\\d+)"'))
            return m ? Number(m[1]) : 0
          }
          tests += readNum("tests")
          bad += readNum("failures") + readNum("errors")
        }
        grandTotal += tests
        grandFailures += bad
        lines.push(name + ": " + tests + " tests, " + bad + " failures+errors")
      }
      lines.push("TOTAL: " + grandTotal + " tests, " + grandFailures + " failures+errors")

      const allLines = result.out.split(/\r?\n/)
      const compilerErrors = allLines.filter((l) => /^e:\s/.test(l)).slice(0, 25)
      const failedTasks = allLines
        .filter((l) => /FAILED|Execution failed for task/.test(l))
        .slice(0, 25)

      if (compilerErrors.length) {
        lines.push("", "COMPILER ERRORS:")
        lines.push.apply(lines, compilerErrors)
      }
      if (failedTasks.length) {
        lines.push("", "FAILED TASKS:")
        lines.push.apply(lines, failedTasks)
      }
      if (result.code !== 0 && result.err) {
        const tail = result.err.split(/\r?\n/).slice(-15)
        lines.push("", "STDERR tail:")
        lines.push.apply(lines, tail)
      }

      if (args.include_assemble) {
        const asm = await run(
          "gradlew.bat",
          [":app:assemblePlayDebug", ":app:assembleEnterpriseDebug", "--console=plain"],
          dir
        )
        lines.push(
          "",
          "assemble: " +
            (/BUILD SUCCESSFUL/.test(asm.stdout) ? "BUILD SUCCESSFUL" : "FAILED") +
            " (exit=" + asm.code + ")"
        )
      }

      lines.push(
        "",
        "Note: the unit tests are the host gate. Assembly is reported separately and is not a",
        "correctness claim. Known flake: AndroidKeyProviderTest.keySurvivesNewInstance times out",
        "under parallel flavor load and passes on re-run; unresolved, not a regression."
      )
      return lines.join("\n")
    },
  }),

  atlas_device: tool({
    description:
      "Drive the attached Android device for Sovereign Atlas smoke tests. Actions: devices, " +
      "install, launch, stop, clear_logs, logs, screenshot, profile_help. Targets the Moto G " +
      "ZT4222BMWN on its wireless-TCP transport. Note: CoT marker drops require Network " +
      "Profile Off-Grid Mesh Only or Hybrid; Radio Silence and Cloud-Only refuse by design.",
    args: {
      action: tool.schema
        .enum([
          "devices",
          "install",
          "launch",
          "stop",
          "clear_logs",
          "logs",
          "screenshot",
          "profile_help",
        ])
        .describe("Operation to perform."),
      apk: tool.schema.string().optional().describe("APK path for action=install."),
      grep: tool.schema.string().optional().describe("Regex filter for action=logs."),
      tail: tool.schema
        .number()
        .optional()
        .describe("Tail line count for action=logs, e.g. 400."),
      out_name: tool.schema
        .string()
        .optional()
        .describe("File name for action=screenshot, e.g. smoke1.png."),
    },
    async execute(args, ctx) {
      const device = "adb-ZT4222BMWN-ux3EQE._adb-tls-connect._tcp"
      const pkg = "com.sovereignatlas.atlas"
      const adb = (a, t) => run("adb", ["-s", device].concat(a), ctx.worktree, t || 300000)
      const short = (r) => (r.stdout + r.stderr).trim() || "exit=" + r.code

      switch (args.action) {
        case "devices": {
          const r = await run("adb", ["devices", "-l"], ctx.worktree, 60000)
          return r.stdout.trim() || r.stderr.trim() || "no output"
        }
        case "install": {
          if (!args.apk) return "install requires an apk path"
          const abs = args.apk.includes(":\\") || args.apk.startsWith("/")
            ? args.apk
            : ctx.worktree + "\\" + args.apk
          const exists = await fs.stat(abs).then(
            () => true,
            () => false
          )
          if (!exists) return "APK not found: " + abs
          return short(await adb(["install", "-r", abs]))
        }
        case "launch": {
          await adb(["shell", "am", "force-stop", pkg])
          return short(
            await adb(["shell", "am", "start", "-n", pkg + "/.MainActivity"])
          )
        }
        case "stop":
          return short(await adb(["shell", "am", "force-stop", pkg]))
        case "clear_logs":
          return "logcat cleared: " + short(await adb(["logcat", "-c"]))
        case "logs": {
          const a = ["logcat", "-d"]
          if (args.tail) a.push("-t", String(args.tail))
          const r = await adb(a)
          const noise =
            /SurfaceFlinger|WindowManager|nativeloader|vulkan|choreographer|lowmemorykiller/i
          let lines = (r.stdout + "\n" + r.stderr).split(/\r?\n/)
          lines = lines.filter((l) => l.trim() && !noise.test(l))
          if (args.grep) {
            const re = new RegExp(args.grep, "i")
            lines = lines.filter((l) => re.test(l))
          }
          return lines.slice(-200).join("\n") || "no matching log lines"
        }
        case "screenshot": {
          const name = args.out_name || "atlas-" + Date.now() + ".png"
          const remote = "/sdcard/" + name
          const s = await adb(["shell", "screencap", "-p", remote])
          if (s.code !== 0) return "screencap failed: " + s.stderr.trim()
          const local =
            (process.env.TEMP || "C:\\Users\\612co\\AppData\\Local\\Temp") +
            "\\opencode\\" + name
          await fs.mkdir(local.slice(0, local.lastIndexOf("\\")), { recursive: true })
          const p = await adb(["pull", remote, local])
          return "pulled to " + local + "\n" + p.stdout.trim()
        }
        case "profile_help":
          return [
            "Network Profile gates CoT marker drops (sendMarker refuses otherwise):",
            "",
            "  1. atlas_device action=launch",
            "  2. Tap the Tools FAB (bottom-right), then Settings.",
            "  3. Network Profile -> 'Off-Grid Mesh Only' or 'Hybrid Bridge Mode'.",
            "",
            "Radio Silence (EMCON) throws 'Markers require an active mesh profile'.",
            "Cloud-Only Chat also refuses marker sends.",
            "",
            "Long-press to open the targeting sheet:",
            "  adb -s <device> shell input swipe X Y X Y 1200",
            "Do not drop a marker at screen centre; the green position puck covers it.",
            "A drawing mode being active consumes the long press meant for the sheet.",
          ].join("\n")
        default:
          return "unknown action " + args.action
      }
    },
  }),

  atlas_maplibre_probe: tool({
    description:
      "Verify a Gradle-cached class signature with javap before writing code against it " +
      "(RULES.md 3). Handles Android AARs by extracting classes.jar first, because javap " +
      "cannot read an .aar directly. Use before any MapLibre call you are not certain exists: " +
      "getter casing bites (latitudeSouth, minZoom), withSourceLayer exists only on concrete " +
      "FillLayer/LineLayer and not on the abstract Layer, and getSourceAs returns null rather " +
      "than throwing. A miss is a real answer: do not assume the class exists.",
    args: {
      class: tool.schema.string().describe("Fully qualified class name."),
      grep: tool.schema
        .string()
        .optional()
        .describe("Only show members whose signature matches this regex."),
      filter: tool.schema
        .string()
        .optional()
        .describe("Regex to select cache group directories. Defaults to maplibre; use '.' for all."),
    },
    async execute(args, ctx) {
      const home = process.env.USERPROFILE || "C:\\Users\\612co"
      const cacheRoot = home + "\\.gradle\\caches\\modules-2\\files-2.1"
      const javap = "C:\\Users\\612co\\.jdks\\jbr-21.0.11\\bin\\javap.exe"
      const filter = args.filter || "maplibre"

      let groups = []
      try {
        groups = (await fs.readdir(cacheRoot, { withFileTypes: true }))
          .filter((e) => e.isDirectory() && new RegExp(filter, "i").test(e.name))
          .map((e) => cacheRoot + "\\" + e.name)
      } catch {
        return "Gradle cache not found at " + cacheRoot
      }
      if (!groups.length) {
        return "No cache group matched /" + filter + "/ under " + cacheRoot
      }

      const work = (process.env.TEMP || "C:\\Users\\612co\\AppData\\Local\\Temp") +
        "\\opencode\\aarprobe"
      await fs.mkdir(work, { recursive: true }).catch(() => {})

      const classpath = []
      const jars = []
      const aars = []
      for (const groupDir of groups) {
        for (const mod of await fs.readdir(groupDir, { withFileTypes: true })) {
          if (!mod.isDirectory()) continue
          for (const ver of await fs.readdir(groupDir + "\\" + mod.name, { withFileTypes: true })) {
            if (!ver.isDirectory()) continue
            const verDir = groupDir + "\\" + mod.name + "\\" + ver.name
            for (const inner of await fs.readdir(verDir, { withFileTypes: true })) {
              if (!inner.isDirectory()) continue
              const innerDir = verDir + "\\" + inner.name
              for (const name of await fs.readdir(innerDir).catch(() => [])) {
                const full = innerDir + "\\" + name
                if (name.endsWith(".jar")) {
                  if (/-sources/.test(name)) continue
                  jars.push(full)
                } else if (name.endsWith(".aar")) {
                  aars.push(full)
                }
              }
            }
          }
        }
      }

      // javap cannot read an .aar, so lift classes.jar out of each one first.
      for (const aar of aars) {
        const target = work + "\\" + aar.replace(/[^\w.-]/g, "_") + ".jar"
        const exists = await fs.stat(target).then(
          () => true,
          () => false
        )
        if (!exists) {
          const r = await run("tar", ["-xf", aar, "-C", work, "classes.jar"], work, 120000)
          void r
          const extracted = work + "\\classes.jar"
          const ok = await fs.stat(extracted).then(
            () => true,
            () => false
          )
          if (!ok) continue
          await fs.rename(extracted, target).catch(() => {})
        }
        classpath.push(target)
      }
      classpath.push.apply(classpath, jars)

      if (!classpath.length) {
        return "Found no usable jars or AARs for /" + filter + "/"
      }

      const r = await run(
        javap,
        ["-classpath", classpath.join(";"), args.class],
        ctx.worktree,
        180000
      )
      const text = (r.stdout + r.stderr).split(/\r?\n/)
      const hit = text.some((l) => l.indexOf(args.class) >= 0)
      if (!hit) {
        return [
          args.class + " NOT FOUND in the Gradle cache.",
          "Searched " + classpath.length + " artifact(s) matching /" + filter + "/.",
          "Do not assume this class or method exists. Check the declared dependency in",
          "apps/atlas-android/app/build.gradle and read that version's real API.",
        ].join("\n")
      }
      const body = args.grep
        ? text.filter((l) => new RegExp(args.grep, "i").test(l))
        : text
      return args.class + "\n" + body.join("\n")
    },
  }),
}

export default async function atlasToolsPlugin() {
  return {
    tool: tools,
  }
}
