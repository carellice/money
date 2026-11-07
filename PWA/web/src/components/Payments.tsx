import { useEffect, useState } from 'react'
import {
  accountMethods, accountOf, argb, methodKey, paletteAll, paymentChoice, paymentLabel, paymentMethods, removablePayment, sameChoice,
  type CustomPayment, type PaymentChoice, type PaymentMethod,
} from '../../../shared/model.ts'
import { actions, useAccounts, useHiddenPayments, usePaymentMethods, useStore } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import { AppIcon, Bouncy, ColorSwatches, FormLabel, Icon, IconPicker, NoteField, Pill, PrimaryButton } from './ui.tsx'
import { AlertDialog, FullPage, toast } from './overlays.tsx'

/** Rounded badge with the method's icon on its color. */
export function PaymentBadge({ method, size = 40 }: { method: CustomPayment; size?: number }) {
  return (
    <div className="center" style={{ width: size, height: size, borderRadius: size * 0.36, background: argb(method.color), flexShrink: 0 }}>
      <AppIcon name={method.icon} size={size * 0.5} color={Ink} />
    </div>
  )
}

const newPayment = (count: number): CustomPayment =>
  ({ id: 0, name: '', icon: 'card', color: paletteAll[(count + 3) % paletteAll.length], sortOrder: 0 })

/** Every payment method, built-in first (except the [hidden] ones), as choices. */
export function allChoices(custom: CustomPayment[], hidden: PaymentMethod[] = []): PaymentChoice[] {
  return [
    ...paymentMethods.filter((m) => !hidden.includes(m.id)).map((m): PaymentChoice => ({ method: m.id, customId: null })),
    ...custom.map((m): PaymentChoice => ({ method: 'OTHER', customId: m.id })),
  ]
}

/** Badge of any payment method: the custom ones keep their color. */
function ChoiceBadge({ choice, custom, color, size = 40 }: { choice: PaymentChoice; custom: CustomPayment[]; color: string; size?: number }) {
  const own = choice.customId != null ? custom.find((c) => c.id === choice.customId) : undefined
  return (
    <div className="center" style={{ width: size, height: size, borderRadius: size * 0.36, background: own ? argb(own.color) : color, flexShrink: 0 }}>
      <AppIcon name={paymentLabel(choice, custom).icon} size={size * 0.5} color={Ink} />
    </div>
  )
}

/**
 * The chosen payment method as one field; tapping it opens the list of every method, grouped
 * by the account it moves, plus "Nuovo" to add one on the spot (selected as soon as it is saved).
 */
export function PaymentPicker({ selected, onSelect, color = 'var(--accent)' }: { selected: PaymentChoice; onSelect: (c: PaymentChoice) => void; color?: string }) {
  const custom = usePaymentMethods()
  const hidden = useHiddenPayments()
  const accounts = useAccounts()
  const [open, setOpen] = useState(false)
  const [creating, setCreating] = useState<CustomPayment | null>(null)
  const account = accountOf(selected, accounts)
  const choices = allChoices(custom, hidden)
  // A removed built-in method (the default "Carta", say) gives way to the first one left.
  useEffect(() => {
    if (selected.customId == null && hidden.includes(selected.method)) onSelect(choices[0])
  }, [selected, hidden])
  const groups = [
    ...accounts.map((a) => ({ title: a.name, items: choices.filter((c) => accountMethods(a).includes(methodKey(c))) })),
    { title: accounts.length ? 'Senza conto' : '', items: choices.filter((c) => !accountOf(c, accounts)) },
  ].filter((g) => g.items.length > 0)
  return (
    <>
      <Bouncy pressedScale={0.98} onClick={() => setOpen(true)} className="row" style={{ borderRadius: 22, background: 'var(--surface)', padding: '12px 18px' }}>
        <ChoiceBadge choice={selected} custom={custom} color={color} />
        <div className="col flex1" style={{ marginLeft: 14, minWidth: 0 }}>
          <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>{paymentLabel(selected, custom).label}</span>
          {account && <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>{account.name}</span>}
        </div>
        <span className="t-label" style={{ color: 'var(--inkSoft)' }}>Cambia</span>
        <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
      </Bouncy>
      {open && (
        <FullPage title="Metodo di pagamento" onDismiss={() => setOpen(false)}>
          <div className="col" style={{ padding: '0 20px 32px', gap: 8 }}>
            {groups.map((g) => (
              <div key={g.title} className="col" style={{ gap: 8 }}>
                {g.title && <FormLabel text={g.title} />}
                {g.items.map((choice) => {
                  const isSelected = sameChoice(selected, choice)
                  return (
                    <Bouncy key={methodKey(choice)} pressedScale={0.98} className="row"
                      onClick={() => { onSelect(choice); setOpen(false) }}
                      style={{ borderRadius: 22, padding: '10px 18px', background: isSelected ? 'var(--surfaceHigh)' : 'var(--surface)' }}>
                      <ChoiceBadge choice={choice} custom={custom} color={color} size={36} />
                      <span className="t-bodyStrong flex1 ellipsis" style={{ marginLeft: 14, color: 'var(--ink)' }}>{paymentLabel(choice, custom).label}</span>
                      {isSelected && <Icon name="check" color="var(--ink)" />}
                    </Bouncy>
                  )
                })}
              </div>
            ))}
            <div style={{ height: 8 }} />
            <Pill text="Nuovo metodo" selected={false} onClick={() => setCreating(newPayment(custom.length))} color={color} icon="add" />
          </div>
        </FullPage>
      )}
      {creating && (
        <PaymentEditor original={creating} onDismiss={() => setCreating(null)} onDelete={() => {}}
          onSave={(m) => {
            setCreating(null)
            setOpen(false)
            actions.savePaymentMethod(m)
              .then((id) => onSelect({ method: 'OTHER', customId: id }))
              .catch((e) => toast(e.message, true))
          }} />
      )}
    </>
  )
}

/** The payment methods in the settings: built-in ones can be removed, custom ones edited, plus the button to add one. */
export function PaymentsSection() {
  const custom = usePaymentMethods()
  const hidden = useHiddenPayments()
  const [editing, setEditing] = useState<CustomPayment | null>(null)
  const [removing, setRemoving] = useState<PaymentChoice | null>(null)
  const builtIn = allChoices([], hidden).filter((c) => removablePayment(c.method))
  return (
    <>
      {builtIn.map((c) => (
        <Bouncy key={c.method} pressedScale={0.98} onClick={() => setRemoving(c)} className="row"
          style={{ borderRadius: 22, background: 'var(--surface)', padding: '12px 18px' }}>
          <ChoiceBadge choice={c} custom={custom} color="var(--surfaceHigh)" />
          <span className="t-bodyStrong flex1" style={{ marginLeft: 14, color: 'var(--ink)' }}>{paymentLabel(c, custom).label}</span>
          <Icon name="delete" color="var(--inkFaint)" />
        </Bouncy>
      ))}
      {custom.map((m) => (
        <Bouncy key={m.id} pressedScale={0.98} onClick={() => setEditing(m)} className="row"
          style={{ borderRadius: 22, background: 'var(--surface)', padding: '12px 18px' }}>
          <PaymentBadge method={m} />
          <span className="t-bodyStrong flex1" style={{ marginLeft: 14, color: 'var(--ink)' }}>{m.name}</span>
          <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
        </Bouncy>
      ))}
      <Bouncy pressedScale={0.98} className="row" style={{ borderRadius: 22, background: 'var(--surface)', padding: '16px 18px' }}
        onClick={() => setEditing(newPayment(custom.length))}>
        <Icon name="add" size={22} color="var(--accentText)" />
        <div className="col flex1" style={{ marginLeft: 14 }}>
          <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>Aggiungi metodo di pagamento</span>
          <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>Una carta specifica, i buoni pasto... oltre a carta, contanti e gli altri</span>
        </div>
      </Bouncy>
      {editing && (
        <PaymentEditor original={editing} onDismiss={() => setEditing(null)}
          onSave={(m) => { setEditing(null); actions.savePaymentMethod(m).catch((e) => toast(e.message, true)) }}
          onDelete={(m) => { setEditing(null); setRemoving({ method: 'OTHER', customId: m.id }) }} />
      )}
      {removing && <RemovePayment from={removing} onDone={() => setRemoving(null)} />}
    </>
  )
}

/**
 * Removes a payment method. When movements or fixed expenses are paid with it, first asks which
 * other method they move to.
 */
function RemovePayment({ from, onDone }: { from: PaymentChoice; onDone: () => void }) {
  const data = useStore((s) => s.data)
  const custom = usePaymentMethods()
  const hidden = useHiddenPayments()
  const [target, setTarget] = useState<PaymentChoice | null>(null)
  const uses = [...(data?.transactions ?? []), ...(data?.recurring ?? [])].filter((row) => sameChoice(paymentChoice(row), from)).length
  const name = paymentLabel(from, custom).label
  const remove = (to: PaymentChoice | null) => {
    onDone()
    actions.removePaymentMethod(from, to).catch((e) => toast(e.message, true))
  }
  if (uses === 0) {
    return (
      <AlertDialog title={`Eliminare ${name}?`} text="Nessun movimento o spesa fissa è pagato così."
        confirm="Elimina" confirmColor="var(--expenseText)" onDismiss={onDone} onConfirm={() => remove(null)} />
    )
  }
  const others = allChoices(custom, hidden).filter((c) => !sameChoice(c, from))
  return (
    <FullPage title={`Elimina ${name}`} onDismiss={onDone}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 8 }}>
        <span className="t-body" style={{ color: 'var(--inkSoft)', paddingBottom: 8 }}>
          {uses === 1 ? '1 movimento o spesa fissa è pagato' : `${uses} movimenti e spese fisse sono pagati`} con {name}: scegli il metodo su cui spostarli.
        </span>
        {others.map((choice) => {
          const isSelected = target != null && sameChoice(target, choice)
          return (
            <Bouncy key={methodKey(choice)} pressedScale={0.98} className="row" onClick={() => setTarget(choice)}
              style={{ borderRadius: 22, padding: '10px 18px', background: isSelected ? 'var(--surfaceHigh)' : 'var(--surface)' }}>
              <ChoiceBadge choice={choice} custom={custom} color="var(--accent)" size={36} />
              <span className="t-bodyStrong flex1 ellipsis" style={{ marginLeft: 14, color: 'var(--ink)' }}>{paymentLabel(choice, custom).label}</span>
              {isSelected && <Icon name="check" color="var(--ink)" />}
            </Bouncy>
          )
        })}
        <div style={{ height: 16 }} />
        <PrimaryButton text="Sposta ed elimina" icon="delete" enabled={target != null} color="var(--expense)" onClick={() => target && remove(target)} />
      </div>
    </FullPage>
  )
}

function PaymentEditor({ original, onDismiss, onSave, onDelete }: {
  original: CustomPayment
  onDismiss: () => void
  onSave: (m: CustomPayment) => void
  onDelete: (m: CustomPayment) => void
}) {
  const [name, setName] = useState(original.name)
  const [icon, setIcon] = useState(original.icon)
  const [color, setColor] = useState(original.color)
  const preview = { ...original, name, icon, color }
  return (
    <FullPage title={original.id === 0 ? 'Nuovo metodo' : 'Modifica metodo'} onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <div className="center" style={{ paddingTop: 4 }}>
          <PaymentBadge method={preview} size={96} />
        </div>
        <FormLabel text="Nome" />
        <NoteField value={name} onChange={setName} placeholder="Es. Carta Revolut" />
        <FormLabel text="Icona" />
        <IconPicker selected={icon} color={argb(color)} onSelect={setIcon} />
        <FormLabel text="Colore" />
        <ColorSwatches colors={paletteAll} selected={color} onSelect={setColor} />
        <div style={{ height: 24 }} />
        <PrimaryButton text="Salva" icon="check" enabled={!!name.trim()} color={argb(color)} onClick={() => onSave({ ...preview, name: name.trim() })} />
        {original.id !== 0 && (
          <Bouncy onClick={() => onDelete(original)} className="t-bodyStrong"
            style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999 }}>Elimina metodo di pagamento</Bouncy>
        )}
      </div>
    </FullPage>
  )
}
