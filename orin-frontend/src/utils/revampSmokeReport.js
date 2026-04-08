export const REVAMP_SMOKE_SCHEMA_VERSION = '1.0.0'

export function buildSmokeReport({
  source = 'unknown',
  rows = [],
  generatedAt = new Date().toISOString(),
  lastRunAt = '',
  executed = true,
  stageMatrix = []
} = {}) {
  const failed = rows.filter((row) => !row.passed).length
  const total = rows.length

  return {
    schemaVersion: REVAMP_SMOKE_SCHEMA_VERSION,
    source,
    generatedAt,
    summary: {
      executed,
      passed: failed === 0,
      total,
      failed,
      lastRunAt
    },
    rows,
    stageMatrix
  }
}

export function buildReasonStats(rows = []) {
  const grouped = rows
    .filter((row) => !row.passed)
    .reduce((acc, row) => {
      const reason = row.reason || '未知原因'
      acc[reason] = (acc[reason] || 0) + 1
      return acc
    }, {})

  return Object.entries(grouped).map(([reason, count]) => ({ reason, count }))
}

export function toConsoleLines(report) {
  const lines = []
  lines.push('ORIN Revamp Smoke Report')
  lines.push('')
  lines.push('Checked mappings:')
  for (const row of report.rows || []) {
    const view = row.v2File || row.v2View || '-'
    lines.push(`- ${row.flag || row.key} -> ${row.route} -> ${view}`)
  }
  lines.push('')
  lines.push('Recommended rollout matrix:')
  for (const stage of report.stageMatrix || []) {
    lines.push(`- stage-${stage.stage}: ${stage.flags.join(' + ')}`)
  }
  lines.push('')
  lines.push(`Smoke result: ${report.summary?.passed ? 'PASSED' : 'FAILED'}`)

  if (!report.summary?.passed) {
    const failedRows = (report.rows || []).filter((row) => !row.passed)
    for (const row of failedRows) {
      lines.push(`- ${row.flag || row.key}: ${row.reason || 'failed'}`)
    }
  }

  return lines
}

export function toMarkdownSummary(report) {
  const status = report.summary?.passed ? 'PASSED' : 'FAILED'
  const lines = []
  lines.push('## ORIN Revamp Smoke')
  lines.push('')
  lines.push(`- Status: **${status}**`)
  lines.push(`- Total: ${report.summary?.total || 0}`)
  lines.push(`- Failed: ${report.summary?.failed || 0}`)
  lines.push(`- Generated At: ${report.generatedAt}`)
  lines.push('')
  lines.push('| Flag | Route | Status | Reason |')
  lines.push('| --- | --- | --- | --- |')
  for (const row of report.rows || []) {
    lines.push(`| ${row.flag || row.key} | ${row.route} | ${row.passed ? 'OK' : 'FAIL'} | ${row.reason || '-'} |`)
  }
  return lines.join('\n')
}
