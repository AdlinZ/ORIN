const key = 'orin_ff_showMaturityBadge'

const snippet = `(() => {\n  return {\n    showMaturityBadge: localStorage.getItem('${key}') ?? '(default)'\n  };\n})()`

console.log('ORIN Feature Snapshot Console Snippet:')
console.log('')
console.log(snippet)
console.log('')
console.log('Note: revamp* runtime flags were removed; only showMaturityBadge remains.')
