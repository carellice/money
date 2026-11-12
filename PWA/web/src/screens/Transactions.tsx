import { useMemo, useState } from 'react'
import { monthOf } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import { methodKey, paymentChoice, paymentLabel, sameChoice, type PaymentChoice, type Transaction } from '../../../shared/model.ts'
import { navigate } from '../lib/nav.ts'
import { actions, setMonth, useStore } from '../lib/store.ts'
import { EmptyState, HideAmountsButton, Icon, Masked, MonthSwitcher, Pill, Segmented, Staggered, PageTitle } from '../components/ui.tsx'
import { allChoices } from '../components/Payments.tsx'
import { SwipeRow, TransactionRow } from '../components/TransactionRow.tsx'
import { showSnackbar, toast } from '../components/overlays.tsx'

export function TransactionsScreen() {
  const month = useStore((s) => s.month)
  const data = useStore((s) => s.data)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState(0) // 0 all, 1 expenses, 2 incomes
  const [method, setMethod] = useState<PaymentChoice | null>(null) // null: every method

  const state = useMemo(() => {
    if (!data) return null
    const byId = new Map(data.categories.map((c) => [c.id, c]))
    const memberNames = new Map(data.members.map((m) => [m.id, m.name]))
    const needle = query.trim().toLowerCase()
    const custom = data.paymentMethods ?? []
    // Payment methods used this month, in the usual order: the choices of the method filter.
    const order = allChoices(custom).map(methodKey)
    const rank = (c: PaymentChoice) => { const i = order.indexOf(methodKey(c)); return i < 0 ? order.length : i }
    const methods: PaymentChoice[] = []
    for (const c of [...data.transactions.filter((tx) => monthOf(tx.date) === month).map(paymentChoice), ...(method ? [method] : [])]) {
      if (!methods.some((m) => sameChoice(m, c))) methods.push(c)
    }
    methods.sort((a, b) => rank(a) - rank(b))
    const visible = data.transactions.filter((tx) =>
      monthOf(tx.date) === month &&
      (filter === 0 || (filter === 1 && tx.type === 'EXPENSE') || (filter === 2 && tx.type === 'INCOME')) &&
      (!method || sameChoice(paymentChoice(tx), method)) &&
      (!needle || [
        tx.note, byId.get(tx.categoryId)?.name, tx.subcategoryId != null ? byId.get(tx.subcategoryId)?.name : undefined,
        paymentLabel(paymentChoice(tx), custom).label, tx.memberId != null ? memberNames.get(tx.memberId) : undefined,
      ].some((s) => s?.toLowerCase().includes(needle))))
    const groups: { date: number; items: Transaction[]; net: number }[] = []
    for (const tx of visible) {
      let group = groups.find((g) => g.date === tx.date)
      if (!group) groups.push(group = { date: tx.date, items: [], net: 0 })
      group.items.push(tx)
      group.net += tx.type === 'INCOME' ? tx.amountCents : -tx.amountCents
    }
    return {
      groups,
      categories: byId,
      methods,
      custom,
      income: visible.filter((t) => t.type === 'INCOME').reduce((a, t) => a + t.amountCents, 0),
      expense: visible.filter((t) => t.type === 'EXPENSE').reduce((a, t) => a + t.amountCents, 0),
    }
  }, [data, month, query, filter, method])

  const remove = async (tx: Transaction) => {
    const deleting = actions.deleteTransaction(tx)
    const undo = await showSnackbar('Movimento eliminato', 'Annulla')
    if (undo) actions.restoreTransaction(tx, deleting).catch((e: Error) => toast(e.message, true))
    else actions.discardReceipt(tx)
  }

  return (
    <div className="scroll">
      <div style={{ padding: '0 20px 156px' }}>
        <div className="col status-pad">
          <div style={{ paddingTop: 12 }}>
            <div className="row">
              <PageTitle text="Movimenti" style={{ flex: 1, minWidth: 0 }} />
              <HideAmountsButton />
            </div>
            <MonthSwitcher month={month} onChange={setMonth} style={{ marginTop: 8 }} />
            <label className="row" style={{ marginTop: 14, borderRadius: 999, background: 'var(--surface)', padding: '14px 18px', cursor: 'text' }}>
              <Icon name="search" size={20} color="var(--inkFaint)" />
              <input className="field t-body" style={{ marginLeft: 10 }} value={query} onChange={(e) => setQuery(e.target.value)}
                placeholder="Cerca per nota, categoria, persona…" enterKeyHint="search" />
            </label>
            <Segmented options={['Tutti', 'Spese', 'Entrate']} selected={filter} onSelect={setFilter}
              colors={['var(--accent)', 'var(--expense)', 'var(--income)']} style={{ marginTop: 12 }} />
            {state && (state.methods.length > 1 || method) && (
              <div className="hscroll" style={{ gap: 8, marginTop: 10 }}>
                <Pill text="Tutti i metodi" selected={!method} onClick={() => setMethod(null)} />
                {state.methods.map((c) => {
                  const selected = method != null && sameChoice(c, method)
                  const { label, icon } = paymentLabel(c, state.custom)
                  return <Pill key={methodKey(c)} text={label} appIcon={icon} selected={selected} onClick={() => setMethod(selected ? null : c)} />
                })}
              </div>
            )}
            {state && (
              <div className="row" style={{ marginTop: 14 }}>
                <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Entrate&nbsp;</span>
                <Masked className="t-label" color="var(--incomeText)">
                  <span className="t-label" style={{ color: 'var(--incomeText)' }}>{Money.format(state.income)}</span>
                </Masked>
                <span className="flex1" />
                <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Uscite&nbsp;</span>
                <Masked className="t-label" color="var(--ink)">
                  <span className="t-label" style={{ color: 'var(--ink)' }}>{Money.format(state.expense)}</span>
                </Masked>
              </div>
            )}
            <div style={{ height: 8 }} />
          </div>
        </div>
        {state && state.groups.length === 0 && (
          <EmptyState
            title={query.trim() || method ? 'Nessun risultato' : 'Nessun movimento'}
            text={query.trim() ? "Prova con un'altra parola." : method ? 'Prova con un altro metodo.' : 'Questo mese è ancora vuoto.'}
            icon={query.trim() || method ? 'more' : 'wallet'}
          />
        )}
        {state?.groups.map((group, gi) => (
          <div key={`${month}-${group.date}`}>
            <Staggered index={gi} className="row" style={{ padding: '18px 0 4px' }}>
              <span className="t-label flex1" style={{ color: 'var(--inkSoft)' }}>{Dates.dayHeader(group.date)}</span>
              <Masked className="t-caption" color="var(--inkFaint)">
                <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{Money.format(group.net, true)}</span>
              </Masked>
            </Staggered>
            {group.items.map((tx) => (
              <Staggered key={tx.id} index={gi}>
                <SwipeRow onDelete={() => remove(tx)}>
                  <TransactionRow tx={tx} categories={state.categories} onClick={() => navigate('add', { id: tx.id })} />
                </SwipeRow>
              </Staggered>
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}
