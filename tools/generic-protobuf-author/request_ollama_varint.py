# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'apps/atlas-android/build/wo-c-02a-ollama'
MODEL = 'qwen2.5-coder:1.5b'
BASE = 'http://127.0.0.1:11450'
MAX_REPLY = 8 * 1024 * 1024
TIMEOUT = 900
INSTRUCTION = '''Implement a standalone Kotlin/JVM file containing unsigned protobuf varint64 and field-key encoding and decoding. Also return a separate complete JUnit 4 test file. Use no protobuf library, platform framework, project-specific source or external tools.

Encode unsigned 64-bit integer bits as the shortest base-128 varint, retaining all 64 bits. Decode at most ten bytes. Reject a truncated varint, a tenth byte whose continuation bit is set, or a tenth byte with payload larger than one. Accept nonminimal terminating representations that fit in 64 bits. Invalid offsets and malformed values must raise a controlled exception. Do not silently wrap or truncate.

A field key is a single unsigned varint with value (fieldNumber << 3) | wireType. Accept field numbers 1 through 536870911 inclusive, and wire types 0 through 5 inclusive. Reject field number 0, values outside the range, and wire types 6 or 7. Decode the field number and wire type from the same varint. This task only parses keys: matching group boundaries and parsing field values are out of scope.

Provide object WirePrimitives with encodeVarint(value: ULong): ByteArray, decodeVarint(bytes: ByteArray, offset: Int = 0): Pair<ULong, Int> where the second component is the next unread offset, encodeKey(fieldNumber: Int, wireType: Int): ByteArray, and decodeKey(bytes: ByteArray, offset: Int = 0): KeyResult where KeyResult has fieldNumber: Int, wireType: Int and nextOffset: Int. Ensure key validation occurs before narrowing a decoded value.

JUnit 4 tests must cover exact bytes 08 96 01 for field 1 varint 150, zero, 127, 128, the maximum unsigned 64-bit value, maximum valid field number, all six valid wire types, invalid field numbers and wire types, invalid offsets, truncated varints, overflow and excessive varint length, nonminimal terminating encodings, multiple concatenated values with offsets, and deterministic round trips.

Return complete compilable Kotlin source and tests, not pseudocode. Use Kotlin/JVM 2.1.0, JVM target 17 and JUnit 4. For each Kotlin file begin with exactly these four lines:
// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
Add no other code comments. Put each complete file in a separate fenced Kotlin code block with its relative filename immediately before the block. Do not claim legal independence or compatibility beyond the tested generic wire behavior.'''

SOURCE_INSTRUCTION = '''Return one complete Kotlin/JVM source file, with no explanations or tests. Implement only generic protobuf varint and field-key primitives, without external libraries or tools.

Define object WirePrimitives with encodeVarint(value: ULong): ByteArray, decodeVarint(bytes: ByteArray, offset: Int = 0): Pair<ULong, Int>, encodeKey(fieldNumber: Int, wireType: Int): ByteArray and decodeKey(bytes: ByteArray, offset: Int = 0): KeyResult. Define data class KeyResult(fieldNumber: Int, wireType: Int, nextOffset: Int). Decoders return the next unread offset.

Varint encoding stores the least significant seven bits first, sets the continuation bit on every nonfinal byte, shifts the remaining value right by seven and emits one byte for zero. Decoding combines successive seven-bit payloads at increasing shifts. It must stop at ten bytes, reject truncation, reject a tenth byte with continuation set or payload greater than one, and reject invalid offsets with a controlled exception. Preserve all 64 unsigned bits. Accept nonminimal terminated representations that fit.

Field key is one varint (fieldNumber << 3) | wireType, not two values. Field number must be 1 through 536870911 and wire type 0 through 5. Reject all other values. Check decoded field number before narrowing to Int. Key decoding consumes only its key bytes. Field 1 wire type 0 and value 150 encode as 08 96 01. Kotlin version 2.1.0.

The first four lines of the file must be exactly:
// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
No other comments. Return the complete file and nothing else.'''


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_json(opener, path, data=None):
    req = urllib.request.Request(BASE + path, data=data, headers={'Content-Type': 'application/json'}, method='POST' if data is not None else 'GET')
    with opener.open(req, timeout=TIMEOUT) as response:
        raw = response.read(MAX_REPLY + 1)
    if len(raw) > MAX_REPLY:
        raise ValueError('response exceeds recording limit')
    return raw, json.loads(raw)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--send', action='store_true')
    parser.add_argument('--expected-input-sha256')
    parser.add_argument('--source-only', action='store_true')
    parser.add_argument('--model', default=MODEL)
    args = parser.parse_args()
    instruction = SOURCE_INSTRUCTION if args.source_only else INSTRUCTION
    if args.model == 'phi4-mini:latest':
        framed = '<|user|>' + instruction + '<|end|><|assistant|>'
    elif args.model in ('qwen2.5-coder:1.5b', 'qwen2.5:3b'):
        framed = '<|im_start|>user\n' + instruction + '<|im_end|>\n<|im_start|>assistant\n'
    else:
        sys.exit('No reviewed raw prompt template for this model')
    request = {'model': args.model, 'prompt': framed, 'raw': True, 'stream': False, 'options': {'temperature': 0, 'seed': 1729, 'num_ctx': 4096 if args.source_only else 8192, 'num_predict': 1500 if args.source_only else 4096}}
    data = json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    input_hash = sha256(instruction.encode('utf-8'))
    print('Author input SHA-256:', input_hash)
    print('Request SHA-256:', sha256(data))
    print('Model:', args.model, '| raw prompt | no tools or conversation history')
    if not args.send:
        print('Preview only; no request was sent.')
        return
    if args.expected_input_sha256 != input_hash:
        sys.exit('Reviewed input hash missing or mismatched; no request was sent.')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run = OUTPUT / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex)
    run.mkdir(mode=0o700)
    (run / 'request.json').write_bytes(data)
    try:
        tags_raw, tags = read_json(opener, '/api/tags')
        matches = [m for m in tags.get('models', []) if m.get('name') == args.model or m.get('model') == args.model]
        if len(matches) != 1 or not matches[0].get('digest'):
            raise ValueError('expected model and digest are not available')
        version_raw, _ = read_json(opener, '/api/version')
        show_request = json.dumps({'model': args.model}, separators=(',', ':')).encode('utf-8')
        model_raw, _ = read_json(opener, '/api/show', show_request)
        (run / 'tags.json').write_bytes(tags_raw)
        (run / 'version.json').write_bytes(version_raw)
        (run / 'model.json').write_bytes(model_raw)
        response_raw, response = read_json(opener, '/api/generate', data)
        (run / 'response.json').write_bytes(response_raw)
        if response.get('done') is not True or response.get('done_reason') != 'stop' or not isinstance(response.get('response'), str) or not response['response'].strip():
            raise ValueError('generation is incomplete or empty')
        result = response['response'].encode('utf-8')
        (run / 'author-output.txt').write_bytes(result)
        manifest = {'input_sha256': input_hash, 'request_sha256': sha256(data), 'response_sha256': sha256(response_raw), 'output_sha256': sha256(result), 'model_name': args.model, 'model_digest': matches[0]['digest'], 'model_metadata_sha256': sha256(model_raw), 'ollama_version_sha256': sha256(version_raw), 'raw': True, 'tools': [], 'recorded_at_utc': datetime.now(timezone.utc).isoformat()}
        (run / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
        print('Output SHA-256:', manifest['output_sha256'])
        print('Recorded request and response:', run)
    except (urllib.error.URLError, TimeoutError, OSError, ValueError, json.JSONDecodeError) as error:
        sys.exit('Author request failed or incomplete; retained run: ' + str(run) + '; ' + type(error).__name__ + ': ' + str(error))


if __name__ == '__main__':
    main()
