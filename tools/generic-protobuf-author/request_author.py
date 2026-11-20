# ============================================================
# As Above, So Below. As Within, So Without.
# The Future Dictates the Past and the Past is Always Present.
# ============================================================
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / 'docs/atlas-production/evidence/GENERIC_PROTOBUF_WIRE_REQUIREMENTS_DRAFT.md'
OUTPUT = ROOT / 'apps/atlas-android/build/wo-c-02a-author'
INSTRUCTION = '''Implement the supplied generic Protocol Buffers wire-format requirements as a standalone Kotlin/JVM library.

You have no repository access or external tools. Do not reference, reconstruct, or incorporate Meshtastic-specific schemas, generated code, Atlas source, or previous implementations.

Return complete compilable Kotlin source and independent unit tests. Validate bounds, malformed inputs, integer overflow, unknown fields, and unsupported wire types. Do not implement Meshtastic-specific messages.

Do not claim legal independence or protocol compatibility beyond the tested generic protobuf behavior.

For each Kotlin source file begin with exactly these four lines:
// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================
Do not add other code comments. Return each complete source file in a separate fenced code block, with its relative path immediately before the block. Use Kotlin/JVM 2.1.0, JVM target 17 and JUnit 4 for tests.'''


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--send', action='store_true')
    parser.add_argument('--model', default='gpt-6-astra')
    parser.add_argument('--expected-input-sha256')
    args = parser.parse_args()
    spec = SPEC.read_text(encoding='utf-8')
    marker = '## Scope\n'
    if spec.count(marker) != 1:
        sys.exit('Expected exactly one requirements scope heading')
    requirements = marker + spec.split(marker, 1)[1]
    text = INSTRUCTION + '\n\nGeneric requirements:\n\n' + requirements
    request = {'model': args.model, 'store': False, 'tools': [], 'max_output_tokens': 16000, 'input': text}
    request_bytes = json.dumps(request, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
    print('Author input SHA-256:', digest(text.encode('utf-8')))
    print('Request SHA-256:', digest(request_bytes))
    print('Model:', args.model, '| tools: none | stored response: false')
    if not args.send:
        print('Preview only; no request was sent.')
        return
    if args.expected_input_sha256 != digest(text.encode('utf-8')):
        sys.exit('Reviewed input hash missing or mismatched; no request was sent.')
    key = os.environ.get('OPENAI_API_KEY')
    if not key:
        sys.exit('OPENAI_API_KEY is not set; no request was sent.')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    run = OUTPUT / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex)
    run.mkdir(mode=0o700)
    (run / 'request.json').write_bytes(request_bytes)
    req = urllib.request.Request('https://api.openai.com/v1/responses', data=request_bytes, headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=300) as response:
            response_bytes = response.read(16 * 1024 * 1024 + 1)
    except urllib.error.HTTPError as error:
        sys.exit('Request failed with HTTP status ' + str(error.code) + '; no response content recorded.')
    except (urllib.error.URLError, TimeoutError, OSError):
        sys.exit('Request failed before a response was recorded.')
    if len(response_bytes) > 16 * 1024 * 1024:
        sys.exit('Response exceeded recording limit.')
    (run / 'response.json').write_bytes(response_bytes)
    decoded = json.loads(response_bytes)
    blocks = [content['text'] for item in decoded.get('output', []) if item.get('type') == 'message' for content in item.get('content', []) if content.get('type') == 'output_text']
    if decoded.get('status') != 'completed' or not blocks:
        sys.exit('Response was not completed with text output; inspect recorded response.')
    output = '\n'.join(blocks).encode('utf-8')
    (run / 'author-output.txt').write_bytes(output)
    record = {'input_sha256': digest(text.encode('utf-8')), 'request_sha256': digest(request_bytes), 'response_sha256': digest(response_bytes), 'output_sha256': digest(output), 'requested_model': args.model, 'returned_model': decoded.get('model'), 'response_id': decoded.get('id'), 'store': False, 'tools': [], 'recorded_at_utc': datetime.now(timezone.utc).isoformat()}
    (run / 'manifest.json').write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    print('Output SHA-256:', record['output_sha256'])
    print('Recorded request and response:', run)


if __name__ == '__main__':
    main()
