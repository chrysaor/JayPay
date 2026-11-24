import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { readForm } from '@jaypay/ui/form'
import { formatWon } from '@jaypay/ui/format'
import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import { aggregateMoneySum, queryMoneySum } from '../api/money'

function Total({ title, source, region, load }: { title: string; source: string; region: string; load: (region: string) => Promise<number> }) {
  const total = useQuery({ queryKey: ['money-sum', title, region], queryFn: () => load(region), enabled: region !== '' })
  return (
    <Card title={title}>
      <p className="muted">{source}</p>
      {total.data === undefined ? null : <p className="amount">{formatWon(total.data)}</p>}
      {total.isFetching ? <p className="muted">Loading...</p> : null}
      <ErrorNotice error={total.error} />
    </Card>
  )
}

export function RegionalMoney() {
  const [params, setParams] = useSearchParams()
  const region = params.get('region') ?? ''
  return (
    <>
      <h1>Regional money</h1>
      <form
        className="toolbar"
        onSubmit={(event) => {
          setParams({ region: readForm(event).text('region') })
        }}
      >
        <label className="grow">
          Region
          <input name="region" defaultValue={region} placeholder="Seoul" required />
        </label>
        <button className="primary">Look up</button>
      </form>
      {region === '' ? (
        <p className="empty">Enter a region to see the money its members hold.</p>
      ) : (
        <div className="grid">
          <Total
            title="Live aggregation"
            source="Summed now by money-aggregation-service from member balances."
            region={region}
            load={aggregateMoneySum}
          />
          <Total
            title="Query model"
            source="Read from the money-query-service projection."
            region={region}
            load={queryMoneySum}
          />
        </div>
      )}
    </>
  )
}
