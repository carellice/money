import { useMemo, useState } from 'react'
import { monthOf } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import { payment, type Transaction } from '../../../shared/model.ts'
import { navigate } from '../lib/nav.ts'
import { actions, setMonth, useStore } from '../lib/store.ts'
import { EmptyState, Icon, MonthSwitcher, Segmented, Staggered } from '../components/ui.tsx'
import { SwipeRow, TransactionRow } from '../components/TransactionRow.tsx'
import { showSnackbar } from '../components/overlays.tsx'

export function TransactionsScreen() {
  const month = useStore((s) => s.month)
  const data = useStore((s) => s.data)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState(0) // 0 all, 1 expenses, 2 incomes

  const state = useMemo(() => {
    if (!data) return null
    const byId = new Map(data.categories.map((c) => [c.id, c]))
    const memberNames = new Map(data.members.map((m) => [m.id, m.name]))
    const needle = query.trim().toLowerCase()
    const visible = data.transactions.filter((tx) =>
      monthOf(tx.date) === month &&
      (filter === 0 || (filter === 1 && tx.type === 'EXPENSE') || (filter === 2 && tx.type === 'INCOME')) &&
      (!needle || [
        tx.note, byId.get(tx.categoryId)?.name, tx.subcategoryId != null ? byId.get(tx.subcategoryId)?.name : undefined,
        payment(tx.payment).label, tx.memberId != null ? memberNames.get(tx.memberId) : undefined,
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
      income: visible.filter((t) => t.type === 'INCOME').reduce((a, t) => a + t.amountCents, 0),
      expense: visible.filter((t) => t.type === 'EXPENSE').reduce((a, t) => a + t.amountCents, 0),
    }
  }, [data, month, query, filter])

  const remove = async (tx: Transaction) => {
    actions.deleteTransaction(tx)
    const undo = await showSnackbar('Movimento eliminato', 'Annulla')
    if (undo) actions.saveTransaction(tx)
    else actions.discardReceipt(tx)
  }

  return (
    <div className="scroll">
      <div style={{ padding: '0 20px 156px' }}>
        <div className="col status-pad">
          <div style={{ paddingTop: 12 }}>
            <div className="t-display" style={{ color: 'var(--ink)' }}>Movimenti</div>
            <MonthSwitcher month={month} onChange={setMonth} style={{ marginTop: 8 }} />
            <label className="row" style={{ marginTop: 14, borderRadius: 999, background: 'var(--surface)', padding: '14px 18px', cursor: 'text' }}>
              <Icon name="search" size={20} color="var(--inkFaint)" />
              <input className="field t-body" style={{ marginLeft: 10 }} value={query} onChange={(e) => setQuery(e.target.value)}
                placeholder="Cerca per nota, categoria, persona…" enterKeyHint="search" />
            </label>
            <Segmented options={['Tutti', 'Spese', 'Entrate']} selected={filter} onSelect={setFilter}
              colors={['var(--accent)', 'var(--expense)', 'var(--income)']} style={{ marginTop: 12 }} />
            {state && (
              <div className="row" style={{ marginTop: 14 }}>
                <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Entrate&nbsp;</span>
                <span className="t-label" style={{ color: 'var(--incomeText)' }}>{Money.format(state.income)}</span>
                <span className="flex1" />
                <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Uscite&nbsp;</span>
                <span className="t-label" style={{ color: 'var(--ink)' }}>{Money.format(state.expense)}</span>
              </div>
            )}
            <div style={{ height: 8 }} />
          </div>
        </div>
        {state && state.groups.length === 0 && (
          <EmptyState
            title={query.trim() ? 'Nessun risultato' : 'Nessun movimento'}
            text={query.trim() ? "Prova con un'altra parola." : 'Questo mese è ancora vuoto.'}
            icon={query.trim() ? 'more' : 'wallet'}
          />
        )}
        {state?.groups.map((group, gi) => (
          <div key={`${month}-${group.date}`}>
            <Staggered index={gi} className="row" style={{ padding: '18px 0 4px' }}>
              <span className="t-label flex1" style={{ color: 'var(--inkSoft)' }}>{Dates.dayHeader(group.date)}</span>
              <span className="t-caption" style={{ color: 'var(--inkFaint)' }}>{Money.format(group.net, true)}</span>
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
