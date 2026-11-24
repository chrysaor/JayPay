import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { findNormalStatusPayments, requestPayment } from '../api/payment'
import { Outcome } from '../components/Outcome'
import { Panel } from '../components/Panel'
import { readForm } from '../components/form'

const normalStatusPaymentsKey = ['payment', 'normal-status']

export function PaymentScreen() {
  const queryClient = useQueryClient()
  const pending = useQuery({ queryKey: normalStatusPaymentsKey, queryFn: findNormalStatusPayments })
  const pay = useMutation({
    mutationFn: requestPayment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: normalStatusPaymentsKey }),
  })
  return (
    <>
      <h1>결제</h1>
      <Panel title="결제 요청" endpoint="POST /payment/request">
        <form
          onSubmit={(event) => {
            const form = readForm(event)
            pay.mutate({
              requestMembershipId: form.text('requestMembershipId'),
              requestPrice: form.text('requestPrice'),
              franchiseId: form.text('franchiseId'),
              franchiseFeeRate: form.text('franchiseFeeRate'),
            })
          }}
        >
          <label>
            회원 ID
            <input name="requestMembershipId" required />
          </label>
          <label>
            결제 금액
            <input name="requestPrice" type="number" min="1" step="1" required />
          </label>
          <label>
            가맹점 ID
            <input name="franchiseId" required />
          </label>
          <label>
            가맹점 수수료율
            <input name="franchiseFeeRate" required />
          </label>
          <button disabled={pay.isPending}>결제하기</button>
        </form>
        <Outcome state={pay} />
      </Panel>
      <Panel title="정산 대기 결제" endpoint="GET /payment/normal-status">
        <button
          onClick={() => {
            void pending.refetch()
          }}
          disabled={pending.isFetching}
        >
          새로고침
        </button>
        <Outcome state={pending} />
      </Panel>
    </>
  )
}
