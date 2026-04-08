import { FEATURE_FLAG_KEYS } from '../src/config/featureFlags.js'

const lines = FEATURE_FLAG_KEYS.map((flag) => `  "${flag}": localStorage.getItem("orin_ff_${flag}") ?? "(default)"`)

const snippet = `(() => {\n  return {\n${lines.join(',\n')}\n  };\n})()`

console.log('ORIN Revamp Flag Snapshot Console Snippet:')
console.log('')
console.log(snippet)
