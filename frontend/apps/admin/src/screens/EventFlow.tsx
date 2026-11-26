import { Card } from '@jaypay/ui/Card'
import { ErrorNotice } from '@jaypay/ui/ErrorNotice'
import { formatDateTime, formatWon } from '@jaypay/ui/format'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
  findFlow,
  findFlows,
  flowTypeLabel,
  formatGap,
  outcomeSchema,
  stepLabel,
  type FlowDetail,
  type Outcome,
} from '../api/eventFlow'

const outcomes: Record<Outcome, { label: string; tone: 'warn' | 'ok' | 'danger' }> = {
  IN_PROGRESS: { label: 'In progress', tone: 'warn' },
  SUCCEEDED: { label: 'Succeeded', tone: 'ok' },
  FAILED: { label: 'Failed', tone: 'danger' },
}

const listInterval = 5000
const flowInterval = 2000

const clock = new Intl.DateTimeFormat('en-US', { timeStyle: 'medium' })

type Selection = { flowType: string; flowId: string }

function searchOf(status: Outcome | null, selection: Selection | null) {
  return new URLSearchParams({ ...(status ? { status } : {}), ...selection })
}

function OutcomeBadge({ outcome }: { outcome: Outcome }) {
  const { label, tone } = outcomes[outcome]
  return <span className={`badge badge-${tone}`}>{label}</span>
}

function Steps({ flow }: { flow: FlowDetail }) {
  return (
    <>
      <p className="muted">
        {flow.status === 'IN_PROGRESS' ? 'New steps appear here as they happen.' : 'This flow has ended.'}
      </p>
      <dl className="facts">
        <dt>Type</dt>
        <dd>{flowTypeLabel(flow.flowType)}</dd>
        <dt>Member</dt>
        <dd>{flow.membershipId}</dd>
        <dt>Amount</dt>
        <dd>{formatWon(flow.amount)}</dd>
        <dt>Status</dt>
        <dd>
          <OutcomeBadge outcome={flow.status} />
        </dd>
        <dt>Started</dt>
        <dd>{formatDateTime(flow.startedAt)}</dd>
      </dl>
      <ol className="timeline" aria-label="Steps">
        {flow.steps.map((step, index) => {
          const previous = flow.steps[index - 1]
          return (
            <li key={index} className={`step step-${outcomes[step.outcome].tone}`}>
              <div className="step-head">
                <strong>{stepLabel(step.step)}</strong>
                <OutcomeBadge outcome={step.outcome} />
              </div>
              <div className="step-meta">
                <span>{step.service}</span>
                <time dateTime={step.occurredAt}>{clock.format(new Date(step.occurredAt))}</time>
                {previous ? <span>{formatGap(previous.occurredAt, step.occurredAt)}</span> : null}
              </div>
              {step.reason ? <p className="step-reason">{step.reason}</p> : null}
            </li>
          )
        })}
      </ol>
    </>
  )
}

function Timeline({ flowType, flowId }: Selection) {
  const flow = useQuery({
    queryKey: ['event-flow', flowType, flowId],
    queryFn: () => findFlow(flowType, flowId),
    refetchInterval: (query) => (query.state.data?.status === 'IN_PROGRESS' ? flowInterval : false),
  })
  return (
    <Card title={flowId}>
      <ErrorNotice error={flow.error} />
      {flow.data ? <Steps flow={flow.data} /> : null}
    </Card>
  )
}

export function EventFlow() {
  const [params, setParams] = useSearchParams()
  const status = outcomeSchema.safeParse(params.get('status')).data ?? null
  const flowType = params.get('flowType')
  const flowId = params.get('flowId')
  const selection = flowType && flowId ? { flowType, flowId } : null
  const flows = useQuery({
    queryKey: ['event-flows', status],
    queryFn: () => findFlows(status),
    refetchInterval: listInterval,
  })
  return (
    <>
      <h1>Event flow</h1>
      <p className="summary">Recent top-ups, newest first, step by step across the services. The list refreshes every 5 seconds.</p>
      <div className="toolbar">
        <label>
          Status
          <select
            value={status ?? ''}
            onChange={(event) => {
              setParams(searchOf(outcomeSchema.safeParse(event.target.value).data ?? null, selection))
            }}
          >
            <option value="">All</option>
            {outcomeSchema.options.map((outcome) => (
              <option key={outcome} value={outcome}>
                {outcomes[outcome].label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="flow-layout">
        <div className="flow-list">
          <ErrorNotice error={flows.error} />
          {flows.data?.length === 0 ? (
            <p className="empty">{status ? 'No flows have this status.' : 'No flows have started yet.'}</p>
          ) : null}
          {flows.data?.length ? (
            <table>
              <thead>
                <tr>
                  <th>Status</th>
                  <th>Flow</th>
                  <th>Amount</th>
                  <th>Last step</th>
                  <th>Started at</th>
                  <th>Member</th>
                  <th>Type</th>
                </tr>
              </thead>
              <tbody>
                {flows.data.map((flow) => {
                  const chosen = flow.flowType === flowType && flow.flowId === flowId
                  return (
                    <tr key={`${flow.flowType}/${flow.flowId}`} aria-current={chosen ? 'true' : undefined}>
                      <td>
                        <OutcomeBadge outcome={flow.status} />
                      </td>
                      <td>
                        <Link to={`?${searchOf(status, { flowType: flow.flowType, flowId: flow.flowId }).toString()}`}>{flow.flowId}</Link>
                      </td>
                      <td>{formatWon(flow.amount)}</td>
                      <td>{stepLabel(flow.failedStep ?? flow.lastStep)}</td>
                      <td>{formatDateTime(flow.startedAt)}</td>
                      <td>{flow.membershipId}</td>
                      <td>{flowTypeLabel(flow.flowType)}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          ) : null}
        </div>
        {selection ? (
          <Timeline {...selection} />
        ) : (
          <p className="empty">Choose a flow to see its steps.</p>
        )}
      </div>
    </>
  )
}
