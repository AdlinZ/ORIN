import * as echarts from 'echarts/core'
import {
  BarChart,
  CustomChart,
  GaugeChart,
  GraphChart,
  LineChart,
  PieChart,
  TreeChart
} from 'echarts/charts'
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers'

echarts.use([
  BarChart,
  CustomChart,
  GaugeChart,
  GraphChart,
  LineChart,
  PieChart,
  TreeChart,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  LabelLayout,
  SVGRenderer,
  CanvasRenderer
])

const isJsdom = () => (
  typeof navigator !== 'undefined' &&
  /jsdom/i.test(navigator.userAgent || '')
)

const init = (dom, theme, opts = {}) => {
  if (isJsdom()) {
    return echarts.init(dom, theme, {
      ...opts,
      renderer: 'svg'
    })
  }
  return echarts.init(dom, theme, opts)
}

export default {
  ...echarts,
  init
}
export * from 'echarts/core'
