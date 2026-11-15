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
  const present = await fs
    .stat(root)
    .then((s) => s.isDirectory())
    .catch(() => false)
  if (!present) return { files: out, missing: true }
  await walk(root)
  return { files: out, missing: false }
}

function relative(worktree, file) {
  return file.startsWith(worktree) ? file.slice(worktree.length + 1) : file
}

function gradleEnv() {
  return Object.assign({}, process.env, {
    JAVA_HOME: "C:\\Users\\612co\\.jdks\\jbr-21.0.11",
    ANDROID_HOME: "C:\\android",
  })
}

// gradlew.bat cannot be execFile'd directly on Windows. Since the
// CVE-2024-27980 hardening, Node refuses to spawn a .bat/.cmd without
// shell:true and throws EINVAL - which is why every atlas_gates call used to
// die at spawn. Go through cmd.exe, and always hand it the working JDK: the
// Android Studio JBR is stripped (no lib/jvm.cfg) and every Gradle call fails
// against it. One helper so the assemble path cannot forget the env.
function runGradle(tasks, cwd, timeoutMs) {
  const limit = timeoutMs || 3600000
  return new Promise((resolve) => {
    const child = execFile(
      "cmd.exe",
      ["/c", "gradlew.bat"].concat(tasks),
      {
        cwd: cwd,
        env: gradleEnv(),
        timeout: limit,
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
}

const HOST_GATE_SUITES = [
  {
    task: ":atlas-sdk:test",
    modulePrefix: ":atlas-sdk:",
    label: "atlas-sdk:test",
    xmlRelDir: "\\atlas-sdk\\build\\test-results\\test",
  },
  {
    task: ":sdk-consumer:test",
    modulePrefix: ":sdk-consumer:",
    label: "sdk-consumer:test",
    xmlRelDir: "\\sdk-consumer\\build\\test-results\\test",
  },
  {
    task: ":app:testPlayDebugUnitTest",
    modulePrefix: ":app:",
    label: "app:testPlayDebugUnitTest",
    xmlRelDir: "\\app\\build\\test-results\\testPlayDebugUnitTest",
  },
  {
    task: ":app:testEnterpriseDebugUnitTest",
    modulePrefix: ":app:",
    label: "app:testEnterpriseDebugUnitTest",
    xmlRelDir: "\\app\\build\\test-results\\testEnterpriseDebugUnitTest",
  },
]

const tools = {
  atlas_purity_scan: tool({
    description:
      "Audit the Atlas engine/adapters boundary (RULES.md 2.1). Scans the pure-logic " +
      "packages (core, geo, camera, layers, field, measure, goto, track, offline, tactical) " +
      "for forbidden MapLibre, Compose, Android, or AndroidX imports and reports each " +
      "violation with file:line. Use after editing anything in a pure package, and before " +
      "claiming a gate is green. Scope note: RULES.md 2.1 forbids MapLibre/Android/Compose " +
      "only. java.net is NOT in that list, so offline/ legitimately imports ServerSocket and " +
      "HttpURLConnection - do not report those here. A missing package directory or a zero-file " +
      "scan is reported as INCOMPLETE, never as clean.",
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
      const missing = []
      let scanned = 0

      for (const pkg of packages) {
        const found = await kotlinFiles(srcRoot + "\\" + pkg)
        if (found.missing) missing.push(pkg)
        scanned += found.files.length
        for (const file of found.files) {
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

      if (missing.length) {
        return [
          "PURITY SCAN INCOMPLETE - " +
            missing.length +
            " package directory/directories not found: " +
            missing.join(", "),
          "A missing package is NOT a clean result. Either the package was renamed or moved,",
          "or PURE_PACKAGES in atlas-tools.ts names a path that does not exist. Fix one or the",
          "other before trusting any purity claim.",
        ].join("\n")
      }

      if (scanned === 0) {
        return [
          "PURITY SCAN INCONCLUSIVE - 0 Kotlin files scanned across " +
            packages.length +
            " package(s).",
          "Zero files means the source root or the scan path is wrong. This is not a clean result.",
        ].join("\n")
      }

      if (violations.length === 0) {
        return (
          "PURITY CLEAN - " + scanned + " Kotlin files scanned across " +
          packages.length + " package(s): " + packages.join(", ")
        )
      }
      return [
        "PURITY VIOLATIONS - " + violations.length + " found in " + scanned + " files scanned.",
        "Pure packages must not import MapLibre, Compose, Android, or AndroidX (RULES.md 2.1).",
        "Move the logic down, or keep the platform type behind an abstraction.",
        "",
      ].concat(violations).join("\n")
    },
  }),

  atlas_gates: tool({
    description:
      "Run the canonical Sovereign Atlas host verification gate across all four suites " +
      "(:atlas-sdk:test, :sdk-consumer:test, :app:testPlayDebugUnitTest, :app:testEnterpriseDebugUnitTest) " +
      "with --continue, parse JUnit XML across every module, and report failures accurately. " +
      "Does not assemble APKs or touch a device unless include_assemble is set (RULES.md 1.6).",
    args: {
      task: tool.schema
        .string()
        .optional()
        .describe(
          "Space-separated Gradle task override. Defaults to all four canonical host gate suites."
        ),
      include_assemble: tool.schema
        .boolean()
        .optional()
        .describe("Also run both debug assemble tasks. Only on explicit ask (RULES.md 1.6)."),
    },
    async execute(args, ctx) {
      const dir = androidDir(ctx.worktree)
      const requested = args.task
        ? args.task.trim().split(/\s+/).filter(Boolean)
        : HOST_GATE_SUITES.map((s) => s.task)
      const tasks = requested.length ? requested : HOST_GATE_SUITES.map((s) => s.task)
      const selectedSuites = HOST_GATE_SUITES.filter((s) => tasks.includes(s.task))

      const result = await runGradle(
        tasks.concat(["--console=plain", "--continue"]),
        dir
      )

      const allLines = (result.out + "\n" + result.err).split(/\r?\n/)
      const compilerErrors = allLines.filter((l) => /^e:\s/.test(l)).slice(0, 25)
      const failedTasks = allLines
        .filter((l) => /Execution failed for task|^>\s*Task\s+.*FAILED/.test(l))
        .slice(0, 25)

      let grandTotal = 0
      let grandFailures = 0
      let grandSkipped = 0
      const suiteLines = []
      const noResults = []
      const failedCases = []
      const staleOrAbortedSuites = []

      for (const suite of selectedSuites) {
        const xmlDir = dir + suite.xmlRelDir
        let files = []
        try {
          files = (await fs.readdir(xmlDir)).filter((f) => f.endsWith(".xml"))
        } catch {
          noResults.push(suite.label)
          continue
        }
        if (!files.length) {
          noResults.push(suite.label)
          continue
        }
        let tests = 0
        let bad = 0
        let skipped = 0
        for (const file of files) {
          const xml = await fs.readFile(xmlDir + "\\" + file, "utf8")
          const suiteTags = xml.match(/<testsuite\b[^>]*>/g) || []
          for (const tag of suiteTags) {
            const readNum = (attr) => {
              const m = tag.match(new RegExp("\\b" + attr + '="(\\d+)"'))
              return m ? Number(m[1]) : 0
            }
            tests += readNum("tests")
            bad += readNum("failures") + readNum("errors")
            skipped += readNum("skipped")
          }
          if (failedCases.length < 25) {
            const tcRegex = /<testcase\b([^>]*)>([\s\S]*?)<\/testcase>/g
            let m
            while ((m = tcRegex.exec(xml)) !== null) {
              const attrs = m[1]
              const body = m[2]
              if (!/<(?:failure|error)\b/.test(body)) continue
              const nameMatch = attrs.match(/\bname="([^"]*)"/)
              const classMatch = attrs.match(/\bclassname="([^"]*)"/)
              const name = nameMatch ? nameMatch[1] : "unknown"
              const cls = classMatch ? classMatch[1] : ""
              failedCases.push(suite.label + " > " + (cls ? cls + "." : "") + name)
              if (failedCases.length >= 25) break
            }
          }
        }
        grandTotal += tests
        grandFailures += bad
        grandSkipped += skipped
        suiteLines.push(
          suite.label +
            ": " +
            tests +
            " tests, " +
            bad +
            " failures+errors, " +
            skipped +
            " skipped (" +
            files.length +
            " XML)"
        )
        const moduleFailedInLog = failedTasks.some(
          (l) => l.includes(suite.task) || l.includes(suite.modulePrefix)
        )
        if (moduleFailedInLog && bad === 0) {
          staleOrAbortedSuites.push(suite.label)
        }
      }

      const green =
        result.code === 0 &&
        /BUILD SUCCESSFUL/.test(result.out) &&
        grandFailures === 0 &&
        noResults.length === 0 &&
        staleOrAbortedSuites.length === 0

      const lines = []
      lines.push("exit=" + result.code + "  tasks=" + tasks.join(" "))
      lines.push(green ? "BUILD SUCCESSFUL" : "BUILD FAILED")
      lines.push.apply(lines, suiteLines)
      if (noResults.length) {
        lines.push(
          "NO JUnit XML for: " +
            noResults.join(", ") +
            " - those suites did not emit results. Missing XML is NOT a pass."
        )
      }
      if (staleOrAbortedSuites.length) {
        lines.push(
          "STALE/ABORTED SUITE WARNING: " +
            staleOrAbortedSuites.join(", ") +
            " had failing Gradle tasks in output with 0 XML failures (compile/setup failure before test execution)."
        )
      }
      lines.push(
        "TOTAL: " +
          grandTotal +
          " tests, " +
          grandFailures +
          " failures+errors, " +
          grandSkipped +
          " skipped"
      )

      if (failedCases.length) {
        lines.push("", "FAILED TESTS:")
        lines.push.apply(lines, failedCases)
      }
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
        const asm = await runGradle(
          [":app:assemblePlayDebug", ":app:assembleEnterpriseDebug", "--console=plain"],
          dir
        )
        lines.push(
          "",
          "assemble: " +
            (/BUILD SUCCESSFUL/.test(asm.out) ? "BUILD SUCCESSFUL" : "FAILED") +
            " (exit=" + asm.code + ")"
        )
      }

      lines.push(
        "",
        "Note: the unit tests across all four host suites are the canonical host gate. Assembly is " +
          "reported separately and is not a correctness claim. Any non-zero failure, error, skipped suite, " +
          "or timeout is a real failure - do not re-run to make it green (the historical " +
          "AndroidKeyProviderTest.keySurvivesNewInstance teardown race was fixed in CI run #72)."
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
      const device = "7040016025040287"
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
