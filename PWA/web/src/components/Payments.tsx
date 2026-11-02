import { useState } from 'react'
import { argb, paletteAll, paymentMethods, sameChoice, type CustomPayment, type PaymentChoice } from '../../../shared/model.ts'
import { actions, usePaymentMethods } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import { AppIcon, Bouncy, ColorSwatches, FormLabel, Icon, IconPicker, NoteField, Pill, PrimaryButton } from './ui.tsx'
import { AlertDialog, Sheet, toast } from './overlays.tsx'

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

/**
 * Built-in and custom payment methods to pick from, plus "Nuovo" to add one on the spot:
 * the new method is selected as soon as it is saved.
 */
export function PaymentPills({ selected, onSelect, color }: { selected: PaymentChoice; onSelect: (c: PaymentChoice) => void; color?: string }) {
  const custom = usePaymentMethods()
  const [creating, setCreating] = useState<CustomPayment | null>(null)
  return (
    <div className="flow">
      {paymentMethods.map((m) => {
        const choice = { method: m.id, customId: null }
        return <Pill key={m.id} text={m.label} selected={sameChoice(selected, choice)} onClick={() => onSelect(choice)} color={color} appIcon={m.icon} />
      })}
      {custom.map((m) => {
        const choice: PaymentChoice = { method: 'OTHER', customId: m.id }
        return <Pill key={'c' + m.id} text={m.name} selected={sameChoice(selected, choice)} onClick={() => onSelect(choice)} color={argb(m.color)} appIcon={m.icon} />
      })}
      <Pill text="Nuovo" selected={false} onClick={() => setCreating(newPayment(custom.length))} color={color} icon="add" />
      {creating && (
        <PaymentEditor original={creating} onDismiss={() => setCreating(null)} onDelete={() => {}}
          onSave={(m) => {
            setCreating(null)
            actions.savePaymentMethod(m)
              .then((id) => onSelect({ method: 'OTHER', customId: id }))
              .catch((e) => toast(e.message, true))
          }} />
      )}
    </div>
  )
}

/** The custom payment methods in the settings, with the button to add one. */
export function PaymentsSection() {
  const custom = usePaymentMethods()
  const [editing, setEditing] = useState<CustomPayment | null>(null)
  return (
    <>
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
          onDelete={(m) => { setEditing(null); actions.deletePaymentMethod(m).catch((e) => toast(e.message, true)) }} />
      )}
    </>
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
  const [confirmDelete, setConfirmDelete] = useState(false)
  const preview = { ...original, name, icon, color }
  return (
    <Sheet onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <div className="row">
          <PaymentBadge method={preview} size={64} />
          <span className="t-title" style={{ marginLeft: 14, color: 'var(--ink)' }}>
            {original.id === 0 ? 'Nuovo metodo di pagamento' : 'Modifica metodo di pagamento'}
          </span>
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
          <Bouncy onClick={() => setConfirmDelete(true)} className="t-bodyStrong"
            style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999 }}>Elimina metodo di pagamento</Bouncy>
        )}
      </div>
      {confirmDelete && (
        <AlertDialog title={`Eliminare ${original.name}?`} text={'I movimenti e le spese fisse pagati così restano, con metodo di pagamento "Altro".'}
          confirm="Elimina" confirmColor="var(--expenseText)" onDismiss={() => setConfirmDelete(false)}
          onConfirm={() => { setConfirmDelete(false); onDelete(original) }} />
      )}
    </Sheet>
  )
}
