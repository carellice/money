import { useState } from 'react'
import { Dates, Money } from '../../../shared/format.ts'
import { today, type Month } from '../../../shared/date.ts'
import { argb, type Account } from '../../../shared/model.ts'
import type { SettleUp } from '../../../shared/settle.ts'
import { actions, useAccounts } from '../lib/store.ts'
import { AlertDialog, toast } from '../components/overlays.tsx'
import { Icon, Pill, PrimaryButton, SectionTitle, SoftCard, useHideable } from '../components/ui.tsx'
import { MemberAvatar } from './Settings.tsx'

/**
 * Who owes whom to even out the month: the people split the expenses
 * equally, and once the money has been given the expenses counted so far are marked as settled.
 * Settling up can also register each payment as a transfer between the accounts of the two people.
 */
export function SettleUpCard({ settle, month }: { settle: SettleUp; month: Month }) {
  const hide = useHideable()
  const [confirm, setConfirm] = useState(false)
  const name = Dates.monthName(month).toLowerCase()
  const accounts = useAccounts()
  // Each payment leaves an account of who gives and lands on one of who receives: theirs if they have any, otherwise any.
  const choicesFor = (memberId: number) => {
    const own = accounts.filter((a) => a.memberId === memberId)
    return own.length ? own : accounts
  }
  const firstOf = (memberId: number) => accounts.find((a) => a.memberId === memberId)?.id ?? null
  const [from, setFrom] = useState<(number | null)[]>([])
  const [to, setTo] = useState<(number | null)[]>([])

  const open = () => {
    setFrom(settle.transfers.map((t) => firstOf(t.from.id)))
    setTo(settle.transfers.map((t) => firstOf(t.to.id)))
    setConfirm(true)
  }

  const settleNow = async () => {
    setConfirm(false)
    try {
      await actions.settleTransactions(settle.ids)
      for (const [i, t] of settle.transfers.entries()) {
        const a = from[i], b = to[i]
        if (a == null || b == null || a === b) continue
        await actions.saveTransfer({ id: 0, fromId: a, toId: b, amountCents: t.cents, date: today(), note: `Para i conti · ${name}`, createdAt: Date.now() })
      }
      toast('Conti parati')
    } catch (e) {
      toast((e as Error).message, true)
    }
  }

  return (
    <div className="col">
      <SectionTitle text="Para i conti" />
      <SoftCard>
        {settle.ids.length === 0 ? (
          <>
            <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>
              {settle.settledBefore ? `Conti parati per ${name}.` : `Nessuna spesa da dividere a ${name}.`}
            </span>
            <span className="t-caption" style={{ color: 'var(--inkSoft)', paddingTop: 4 }}>
              {settle.settledBefore ? 'Le prossime spese del mese compariranno qui.' : 'Qui vedi chi deve dare quanto a chi, dividendo le spese in parti uguali.'}
            </span>
          </>
        ) : (
          <>
            <div className="col" style={{ gap: 12 }}>
              {settle.paid.map(({ member, cents }) => (
                <div key={member.id} className="row">
                  <MemberAvatar member={member} size={32} />
                  <span className="t-bodyStrong flex1" style={{ color: 'var(--ink)', marginLeft: 12 }}>{member.name}</span>
                  <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>ha pagato&nbsp;</span>
                  <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{hide(Money.format(cents))}</span>
                </div>
              ))}
            </div>
            <div className="col" style={{ gap: 12, paddingTop: 18 }}>
              {settle.transfers.length === 0
                ? <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>Siete pari: nessuno deve niente.</span>
                : settle.transfers.map((t, i) => (
                  <div key={i} className="row">
                    <MemberAvatar member={t.from} size={28} />
                    <Icon name="arrow_forward" size={16} color="var(--inkFaint)" style={{ margin: '0 4px' }} />
                    <MemberAvatar member={t.to} size={28} />
                    <span className="t-body flex1" style={{ color: 'var(--ink)', marginLeft: 12 }}>
                      <b>{t.from.name}</b> deve dare <b>{hide(Money.format(t.cents))}</b> a <b>{t.to.name}</b>
                    </span>
                  </div>
                ))}
            </div>
            <span className="t-caption" style={{ color: 'var(--inkFaint)', paddingTop: 10 }}>
              {hide(Money.format(settle.total))} divisi in parti uguali tra {settle.paid.length} persone. Le spese escluse dal pagante non contano.
            </span>
            <div style={{ height: 16 }} />
            <PrimaryButton text="Conti parati" onClick={open} icon="check" />
          </>
        )}
      </SoftCard>
      {confirm && (
        <AlertDialog title="Conti parati?"
          text={`Le ${settle.ids.length} spese contate finora non verranno più considerate. Se aggiungi altre spese a ${name}, conteranno solo quelle.`}
          confirm="Sì, parati" onConfirm={settleNow} onDismiss={() => setConfirm(false)}>
          {accounts.length >= 2 && settle.transfers.length > 0 && (
            <div className="col" style={{ paddingTop: 12 }}>
              <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>
                Scegli da quale conto partono i soldi e su quale arrivano: il trasferimento si registra da solo. Senza conti, si segnano solo come parati.
              </span>
              {settle.transfers.map((t, i) => (
                <div key={i} className="col" style={{ paddingTop: 16 }}>
                  <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{t.from.name} dà {hide(Money.format(t.cents))} a {t.to.name}</span>
                  <AccountChoice label="Da" accounts={choicesFor(t.from.id)} selected={from[i] ?? null}
                    onSelect={(id) => setFrom(from.map((v, j) => (j === i ? id : v)))} />
                  <AccountChoice label="A" accounts={choicesFor(t.to.id)} selected={to[i] ?? null}
                    onSelect={(id) => setTo(to.map((v, j) => (j === i ? id : v)))} />
                </div>
              ))}
            </div>
          )}
        </AlertDialog>
      )}
    </div>
  )
}

/** Accounts to pick for one side of a payment; touching the picked one again leaves it out. */
function AccountChoice({ label, accounts, selected, onSelect }: {
  label: string
  accounts: Account[]
  selected: number | null
  onSelect: (id: number | null) => void
}) {
  return (
    <>
      <span className="t-caption" style={{ color: 'var(--inkSoft)', padding: '8px 0 4px' }}>{label}</span>
      <div className="flow">
        {accounts.map((a) => (
          <Pill key={a.id} text={a.name} selected={selected === a.id} appIcon={a.icon} color={argb(a.color)}
            onClick={() => onSelect(selected === a.id ? null : a.id)} />
        ))}
      </div>
    </>
  )
}
