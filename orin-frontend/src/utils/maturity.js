import { UI_TEXT } from '@/constants/uiText'

const MATURITY_TEXT = Object.freeze({
  planned: UI_TEXT.maturity.planned,
  beta: UI_TEXT.maturity.beta,
  available: UI_TEXT.maturity.available,
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
