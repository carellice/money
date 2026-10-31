import { useState } from 'react'
import { Money } from '../../../shared/format.ts'
import { argb, paletteAll, type Category } from '../../../shared/model.ts'
import { close } from '../lib/nav.ts'
import { actions, useStore } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import {
  AmountField, Bouncy, CategoryBadge, ColorSwatches, FormLabel, IconPicker, NoteField, Pill, PrimaryButton, RoundIconButton, Segmented, Staggered, TopBar,
} from '../components/ui.tsx'
import { Sheet, toast } from '../components/overlays.tsx'

type Editing = { category: Category; subs: Category[] }

export function CategoriesScreen() {
  const all = useStore((s) => s.data?.categories ?? [])
  const [tab, setTab] = useState(0)
  const [editing, setEditing] = useState<Editing | null>(null)
  const type = tab === 0 ? 'EXPENSE' : 'INCOME'
  const tops = all.filter((c) => c.parentId == null && c.type === type)

  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title="Categorie e budget" onBack={() => close('categories')} actions={
        <RoundIconButton icon="add" background="var(--accent)" tint={Ink} label="Nuova categoria" onClick={() => setEditing({
          category: {
            id: 0, name: '', icon: 'more', color: paletteAll[Math.floor(Math.random() * paletteAll.length)], type,
            parentId: null, budgetCents: null, sortOrder: tops.length,
          },
          subs: [],
        })} />
      } />
      <div className="scroll">
        <div className="col nav-pad" style={{ padding: '0 20px 40px', gap: 10 }}>
          <div>
            <Segmented options={['Spese', 'Entrate']} selected={tab} onSelect={setTab} colors={['var(--expense)', 'var(--income)']} />
            {tab === 0 && (
              <div className="t-caption" style={{ color: 'var(--inkSoft)', paddingTop: 12 }}>
                Tocca una categoria per rinominarla, cambiare icona, aggiungere sottocategorie o fissare un budget mensile.
              </div>
            )}
          </div>
          {tops.map((cat, i) => {
            const subs = all.filter((c) => c.parentId === cat.id)
            return (
              <Staggered key={`${tab}-${cat.id}`} index={i} step={30}>
                <Bouncy pressedScale={0.97} onClick={() => setEditing({ category: cat, subs })} className="row"
                  style={{ borderRadius: 24, background: 'var(--surface)', padding: 14 }}>
                  <CategoryBadge icon={cat.icon} color={argb(cat.color)} size={46} seed={cat.id} />
                  <div className="col flex1" style={{ marginLeft: 14 }}>
                    <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>{cat.name}</span>
                    <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>
                      {subs.length === 0 ? 'Nessuna sottocategoria' : subs.map((s) => s.name).join(', ')}
                    </span>
                  </div>
                  {cat.budgetCents != null && (
                    <span className="t-caption" style={{ color: Ink, borderRadius: 999, background: argb(cat.color), padding: '4px 10px', whiteSpace: 'nowrap' }}>
                      {Money.short(cat.budgetCents)}/mese
                    </span>
                  )}
                </Bouncy>
              </Staggered>
            )
          })}
        </div>
      </div>
      {editing && (
        <CategoryEditor
          editing={editing}
          onDismiss={() => setEditing(null)}
          onSave={async (cat, subs) => {
            setEditing(null)
            try { await actions.saveCategory(cat, subs) } catch (e) { toast((e as Error).message, true) }
          }}
          onDelete={async (cat) => {
            try {
              await actions.deleteCategory(cat)
              setEditing(null)
            } catch (e) {
              toast((e as Error).message, true)
            }
          }}
        />
      )}
    </div>
  )
}

function CategoryEditor({ editing, onDismiss, onSave, onDelete }: {
  editing: Editing
  onDismiss: () => void
  onSave: (c: Category, subs: Category[]) => void
  onDelete: (c: Category) => void
}) {
  const original = editing.category
  const [name, setName] = useState(original.name)
  const [icon, setIcon] = useState(original.icon)
  const [color, setColor] = useState(original.color)
  const [budget, setBudget] = useState(original.budgetCents != null ? Money.toInput(original.budgetCents) : '')
  const [subs, setSubs] = useState(editing.subs)
  const [newSub, setNewSub] = useState('')

  const addSub = () => {
    const trimmed = newSub.trim()
    if (trimmed && !subs.some((s) => s.name.toLowerCase() === trimmed.toLowerCase())) {
      setSubs([...subs, {
        id: 0, name: trimmed, icon, color, type: original.type, parentId: original.id || null, budgetCents: null, sortOrder: subs.length,
      }])
    }
    setNewSub('')
  }

  return (
    <Sheet onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px' }}>
        <div className="row">
          <CategoryBadge icon={icon} color={argb(color)} size={64} animated />
          <span className="t-title" style={{ marginLeft: 14, color: 'var(--ink)' }}>{original.id === 0 ? 'Nuova categoria' : 'Modifica categoria'}</span>
        </div>
        <FormLabel text="Nome" />
        <NoteField value={name} onChange={setName} placeholder="Es. Palestra" />
        <FormLabel text="Icona" />
        <IconPicker selected={icon} color={argb(color)} onSelect={setIcon} />
        <FormLabel text="Colore" />
        <ColorSwatches colors={paletteAll} selected={color} onSelect={setColor} />
        {original.type === 'EXPENSE' && (
          <>
            <FormLabel text="Budget mensile (facoltativo)" />
            <AmountField value={budget} onChange={setBudget} placeholder="Nessun limite" />
          </>
        )}
        <FormLabel text="Sottocategorie" />
        <div className="flow">
          {subs.map((sub, i) => (
            <Pill key={`${sub.id}-${sub.name}`} text={sub.name} selected onClick={() => setSubs(subs.filter((_, j) => j !== i))} color={argb(color)} icon="close" />
          ))}
        </div>
        <div className="row" style={{ marginTop: 8 }}>
          <NoteField value={newSub} onChange={setNewSub} placeholder="Aggiungi sottocategoria" style={{ flex: 1 }} />
          <div style={{ width: 8 }} />
          <RoundIconButton icon="add" onClick={addSub} background="var(--accent)" tint={Ink} />
        </div>
        <div style={{ height: 24 }} />
        <PrimaryButton text="Salva" icon="check" enabled={!!name.trim()} color={argb(color)}
          onClick={() => onSave({ ...original, name: name.trim(), icon, color, budgetCents: (Money.parse(budget) ?? 0) > 0 ? Money.parse(budget) : null }, subs)} />
        {original.id !== 0 && (
          <Bouncy onClick={() => onDelete(original)} className="t-bodyStrong" style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999 }}>
            Elimina categoria
          </Bouncy>
        )}
      </div>
    </Sheet>
  )
}
