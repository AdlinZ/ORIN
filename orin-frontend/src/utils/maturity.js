const MATURITY_TEXT = Object.freeze({
  planned: '规划中',
  beta: '内测',
  available: '可用'
})

const MATURITY_TAG_TYPE = Object.freeze({
  planned: 'info',
  beta: 'warning',
  available: 'success'
})

export function getMaturityText(status) {
  return MATURITY_TEXT[status] || MATURITY_TEXT.available
}

export function getMaturityTagType(status) {
  return MATURITY_TAG_TYPE[status] || MATURITY_TAG_TYPE.available
}
