import { useEffect, useState } from 'react'
import Highcharts from 'highcharts'
import HighchartsReact from 'highcharts-react-official'
import InfoCard from '../../dashboard/new_components/InfoCard'
import { Spinner } from '@shopify/polaris'
import api from '../api'

// helper to format names
const formatName = (name) => {
  if (!name) return 'Unknown'
  return String(name).replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, l => l.toUpperCase())
}

const hslToHex = (h, s, l) => {
  s /= 100; l /= 100;
  const k = n => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = n => {
    const color = l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
    return Math.round(255 * color).toString(16).padStart(2, '0')
  }
  return `#${f(0)}${f(8)}${f(4)}`
}

const generatePalette = (n) => {
  const out = [];
  for (let i = 0; i < Math.max(1, n); i++) out.push(hslToHex(Math.round((i * 360) / Math.max(1, n)), 65, 50))
  return out
}

// We keep the same export name so the import in the page doesn't change
function ThreatCategoryStackedChart({ startTimestamp, endTimestamp }) {
  const [loading, setLoading] = useState(true)
  const [options, setOptions] = useState(null)

  useEffect(() => {
    let mounted = true
    const load = async () => {
      setLoading(true)
      try {
        // Use the same API as the Sankey which returns categoryCounts: [{category, subCategory, count}, ...]
        const resp = await api.fetchThreatCategoryCount(startTimestamp, endTimestamp)
        if (!mounted) return

        const items = Array.isArray(resp?.categoryCounts) ? resp.categoryCounts : []

        // Build set of categories (x axis) and subcategories (series)
        const categoriesSet = new Set()
        const subSet = new Set()
        items.forEach(it => {
          const cat = formatName(it.category || 'Unknown')
          const sub = formatName(it.subCategory || it.category || 'Unknown')
          categoriesSet.add(cat)
          subSet.add(sub)
        })

        const categories = Array.from(categoriesSet)
        const subs = Array.from(subSet)

        // fallback
        if (categories.length === 0 && subs.length === 0) {
          setOptions(null)
          return
        }

        // initialize series data map: series per subcategory, values per category index
        const dataMap = {}
        subs.forEach(s => { dataMap[s] = new Array(categories.length).fill(0) })

        items.forEach(it => {
          const cat = formatName(it.category || 'Unknown')
          const sub = formatName(it.subCategory || it.category || 'Unknown')
          const idx = categories.indexOf(cat)
          if (idx === -1) return
          if (!dataMap[sub]) dataMap[sub] = new Array(categories.length).fill(0)
          dataMap[sub][idx] += it.count || 0
        })

        const palette = generatePalette(subs.length)
        const series = subs.map((s, i) => ({ name: s, data: dataMap[s], color: palette[i], fillOpacity: 0.6 }))

        const opts = {
          chart: { type: 'area', height: 300, backgroundColor: '#ffffff' },
          title: { text: undefined },
          credits: { enabled: false },
          xAxis: { categories, labels: { style: { color: '#000' } } },
          yAxis: { title: { text: 'Count' }, labels: { style: { color: '#000' } } },
          tooltip: { shared: true, valueSuffix: '', style: { color: '#000' } },
          plotOptions: { area: { stacking: 'normal', marker: { enabled: false } } },
          legend: { align: 'right', verticalAlign: 'top', layout: 'vertical' },
          series
        }

        setOptions(opts)
      } catch (err) {
        if (mounted) setOptions(null)
      } finally {
        if (mounted) setLoading(false)
      }
    }
    load()
    return () => { mounted = false }
  }, [startTimestamp, endTimestamp])

  if (loading) {
    return (
      <InfoCard
        title="Threat Activity (by Category)"
        titleToolTip="Stacked area view showing distribution of activity across categories over time"
        component={<div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '300px' }}><Spinner size="large" /></div>}
      />
    )
  }

  return (
    <InfoCard
      title="Threat Activity (by Category)"
      titleToolTip="Stacked area view showing distribution of activity across categories over time"
      component={
        <div style={{ width: '100%', height: '300px' }}>
          {options ? <HighchartsReact highcharts={Highcharts} options={options} /> : <div style={{padding:20}}>No data</div>}
        </div>
      }
    />
  )
}

export default ThreatCategoryStackedChart
