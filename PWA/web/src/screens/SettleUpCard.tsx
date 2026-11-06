import { useState } from 'react'
import { Dates, Money } from '../../../shared/format.ts'
import type { Month } from '../../../shared/date.ts'
import type { SettleUp } from '../../../shared/settle.ts'
import { actions } from '../lib/store.ts'
import { AlertDialog, toast } from '../components/overlays.tsx'
import { Icon, PrimaryButton, SectionTitle, SoftCard, useHideable } from '../components/ui.tsx'
import { MemberAvatar } from './Settings.tsx'

/**
 * Who owes whom to even out the month: the people split the expenses
 * equally, and once the money has been given the expenses counted so far are marked as settled.
 */
export function SettleUpCard({ settle, month }: { settle: SettleUp; month: Month }) {
  const hide = useHideable()
  const [confirm, setConfirm] = useState(false)
  const name = Dates.monthName(month).toLowerCase()

  const settleNow = async () => {
    setConfirm(false)
    try {
      await actions.settleTransactions(settle.ids)
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
            <PrimaryButton text="Conti parati" onClick={() => setConfirm(true)} icon="check" />
          </>
        )}
      </SoftCard>
      {confirm && (
        <AlertDialog title="Conti parati?"
          text={`Le ${settle.ids.length} spese contate finora non verranno più considerate. Se aggiungi altre spese a ${name}, conteranno solo quelle.`}
          confirm="Sì, parati" onConfirm={settleNow} onDismiss={() => setConfirm(false)} />
      )}
    </div>
  )
}
