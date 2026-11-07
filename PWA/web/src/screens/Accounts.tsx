import { useMemo, useState } from 'react'
import { epochOf, today as todayEpoch } from '../../../shared/date.ts'
import { Dates, Money } from '../../../shared/format.ts'
import {
  accountMethods, accountTransactions, accountTransfers, argb, balanceOf, methodKey, paletteAll, paymentLabel,
  type Account, type Transaction, type Transfer,
} from '../../../shared/model.ts'
import { close, navigate } from '../lib/nav.ts'
import { actions, useAccounts, usePaymentMethods, useStore, useTransfers } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import {
  AmountField, AppIcon, Bouncy, ColorSwatches, FormLabel, Icon, IconPicker, Masked, NoteField, Pill, PrimaryButton, SectionTitle,
  SoftCard, TopBar, useHideable,
} from '../components/ui.tsx'
import { AlertDialog, DatePickerDialog, FullPage, toast } from '../components/overlays.tsx'
import { allChoices } from '../components/Payments.tsx'
import { TransactionRow } from '../components/TransactionRow.tsx'

const newAccount = (count: number): Account => ({
  id: 0, name: '', icon: 'bank', color: paletteAll[(count + 5) % paletteAll.length], methods: '', balanceCents: 0, balanceAt: 0, sortOrder: 0,
})

const failed = (e: Error) => toast(e.message, true)

/** Rounded badge with the account's icon on its color. */
export function AccountBadge({ account, size = 40 }: { account: Account; size?: number }) {
  return (
    <div className="center" style={{ width: size, height: size, borderRadius: size * 0.36, background: argb(account.color), flexShrink: 0 }}>
      <AppIcon name={account.icon} size={size * 0.5} color={Ink} />
    </div>
  )
}

/** What every account holds now, by id. */
function useBalances(): Map<number, number> {
  const accounts = useAccounts()
  const transfers = useTransfers()
  const transactions = useStore((s) => s.data?.transactions)
  return useMemo(
    () => new Map(accounts.map((a) => [a.id, balanceOf(a, accounts, transactions ?? [], transfers)])),
    [accounts, transactions, transfers],
  )
}

function AccountRow({ account, balance, onClick }: { account: Account; balance: number; onClick: () => void }) {
  const hide = useHideable()
  return (
    <Bouncy pressedScale={0.98} onClick={onClick} className="row">
      <AccountBadge account={account} size={36} />
      <span className="t-label flex1 ellipsis" style={{ marginLeft: 12, color: 'var(--ink)' }}>{account.name}</span>
      <span className="t-label" style={{ color: balance < 0 ? 'var(--expenseText)' : 'var(--ink)' }}>{hide(Money.format(balance))}</span>
      <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
    </Bouncy>
  )
}

/** Home: how much money there is, in total and account by account. */
export function AccountsCard() {
  const accounts = useAccounts()
  const balances = useBalances()
  const [editing, setEditing] = useState<Account | null>(null)
  const total = [...balances.values()].reduce((a, b) => a + b, 0)
  return (
    <>
      <SectionTitle text="Disponibile" action={accounts.length ? 'Nuovo conto' : undefined} onAction={() => setEditing(newAccount(accounts.length))} />
      {accounts.length === 0 ? (
        <SoftCard style={{ gap: 14 }}>
          <span className="t-body" style={{ color: 'var(--inkSoft)' }}>Aggiungi i tuoi conti per sapere sempre quanti soldi hai a disposizione.</span>
          <PrimaryButton text="Aggiungi un conto" icon="add" onClick={() => setEditing(newAccount(0))} />
        </SoftCard>
      ) : (
        <SoftCard style={{ gap: 14 }}>
          <Masked className="t-display" color="var(--ink)">
            <span className="t-display" style={{ color: total < 0 ? 'var(--expenseText)' : 'var(--ink)' }}>{Money.format(total)}</span>
          </Masked>
          {accounts.map((a) => (
            <AccountRow key={a.id} account={a} balance={balances.get(a.id) ?? 0} onClick={() => navigate('account', { id: a.id })} />
          ))}
        </SoftCard>
      )}
      {editing && <AccountEditor original={editing} onDismiss={() => setEditing(null)} />}
    </>
  )
}

/** Settings: the accounts, with the button to add one. */
export function AccountsSection() {
  const accounts = useAccounts()
  const [editing, setEditing] = useState<Account | null>(null)
  return (
    <>
      {accounts.map((a) => (
        <Bouncy key={a.id} pressedScale={0.98} onClick={() => navigate('account', { id: a.id })} className="row"
          style={{ borderRadius: 22, background: 'var(--surface)', padding: '12px 18px' }}>
          <AccountBadge account={a} />
          <span className="t-bodyStrong flex1" style={{ marginLeft: 14, color: 'var(--ink)' }}>{a.name}</span>
          <Icon name="keyboard_arrow_right" color="var(--inkFaint)" />
        </Bouncy>
      ))}
      <Bouncy pressedScale={0.98} className="row" style={{ borderRadius: 22, background: 'var(--surface)', padding: '16px 18px' }}
        onClick={() => setEditing(newAccount(accounts.length))}>
        <Icon name="add" size={22} color="var(--accentText)" />
        <div className="col flex1" style={{ marginLeft: 14 }}>
          <span className="t-bodyStrong" style={{ color: 'var(--ink)' }}>Aggiungi conto</span>
          <span className="t-caption" style={{ color: 'var(--inkSoft)' }}>Il conto in banca, i contanti... con i metodi di pagamento che li usano</span>
        </div>
      </Bouncy>
      {editing && <AccountEditor original={editing} onDismiss={() => setEditing(null)} />}
    </>
  )
}

/** One account: its balance, transfers, balance correction and what moved it. */
export function AccountScreen({ id }: { id: number }) {
  const account = useAccounts().find((a) => a.id === id)
  return (
    <div className="col" style={{ height: '100%' }}>
      <TopBar title={account?.name ?? 'Conto'} onBack={() => close('account')} />
      {account && <AccountContent account={account} />}
    </div>
  )
}

type HistoryItem = { tx: Transaction; transfer?: undefined } | { transfer: Transfer; tx?: undefined }

function AccountContent({ account }: { account: Account }) {
  const accounts = useAccounts()
  const transfers = useTransfers()
  const custom = usePaymentMethods()
  const data = useStore((s) => s.data)
  const balance = useBalances().get(account.id) ?? 0
  const [page, setPage] = useState<'edit' | 'transfer' | 'balance' | null>(null)
  const [removing, setRemoving] = useState<Transfer | null>(null)
  const hide = useHideable()

  const categories = useMemo(() => new Map((data?.categories ?? []).map((c) => [c.id, c])), [data])
  const history = useMemo(() => {
    const items: HistoryItem[] = [
      ...accountTransactions(account, accounts, data?.transactions ?? []).map((tx) => ({ tx })),
      ...accountTransfers(account, transfers).map((transfer) => ({ transfer })),
    ]
    const row = (i: HistoryItem) => i.tx ?? i.transfer!
    return items.sort((a, b) => row(b).date - row(a).date || row(b).createdAt - row(a).createdAt)
  }, [account, accounts, transfers, data])
  const names = accountMethods(account).map((key) => {
    const choice = allChoices(custom).find((c) => methodKey(c) === key)
    return choice ? paymentLabel(choice, custom).label : null
  }).filter(Boolean)
  const nameOf = (id: number) => accounts.find((a) => a.id === id)?.name ?? 'conto eliminato'

  return (
    <div className="scroll">
      <div className="col nav-pad" style={{ padding: '0 20px 32px', gap: 12 }}>
        <SoftCard style={{ alignItems: 'center', gap: 6 }}>
          <AccountBadge account={account} size={56} />
          <Masked className="t-display" color="var(--ink)">
            <span className="t-display" style={{ color: balance < 0 ? 'var(--expenseText)' : 'var(--ink)' }}>{Money.format(balance)}</span>
          </Masked>
          <span className="t-caption" style={{ color: 'var(--inkSoft)', textAlign: 'center' }}>
            {names.length ? names.join(' · ') : 'Nessun metodo di pagamento: tocca Modifica per sceglierli'}
          </span>
        </SoftCard>
        <div className="flow">
          <Pill text="Trasferisci" selected={false} icon="swap_vert" onClick={() => {
            if (accounts.length < 2) toast('Per un trasferimento serve almeno un altro conto')
            else setPage('transfer')
          }} />
          <Pill text="Correggi saldo" selected={false} icon="refresh" onClick={() => setPage('balance')} />
          <Pill text="Modifica" selected={false} icon="settings" onClick={() => setPage('edit')} />
        </div>
        <FormLabel text={`Dal ${Dates.long(dayOf(account.balanceAt))}`} />
        {history.length === 0 && (
          <span className="t-body" style={{ color: 'var(--inkSoft)' }}>Ancora nessun movimento su questo conto.</span>
        )}
        {history.map((item) => item.tx ? (
          <TransactionRow key={'t' + item.tx.id} tx={item.tx} categories={categories} showDate={Dates.short(item.tx.date)}
            onClick={() => navigate('add', { id: item.tx.id })} />
        ) : (
          <Bouncy key={'x' + item.transfer.id} pressedScale={0.97} haptics={false} onClick={() => setRemoving(item.transfer)}
            className="row" style={{ padding: '8px 0', gap: 14 }}>
            <div className="center" style={{ width: 46, height: 46, borderRadius: 23, background: 'var(--surfaceHigh)', flexShrink: 0 }}>
              <Icon name="swap_vert" color="var(--ink)" />
            </div>
            <div className="col flex1" style={{ minWidth: 0 }}>
              <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>
                {item.transfer.toId === account.id ? `Da ${nameOf(item.transfer.fromId)}` : `Verso ${nameOf(item.transfer.toId)}`}
              </span>
              <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>
                {['Trasferimento', item.transfer.note.trim(), Dates.short(item.transfer.date)].filter(Boolean).join(' · ')}
              </span>
            </div>
            <span className="t-amount" style={{ color: item.transfer.toId === account.id ? 'var(--incomeText)' : 'var(--ink)', whiteSpace: 'nowrap' }}>
              {hide(Money.format(item.transfer.toId === account.id ? item.transfer.amountCents : -item.transfer.amountCents, true))}
            </span>
          </Bouncy>
        ))}
      </div>
      {page === 'edit' && <AccountEditor original={account} onDismiss={() => setPage(null)} />}
      {page === 'transfer' && <TransferEditor from={account} onDismiss={() => setPage(null)} />}
      {page === 'balance' && <BalanceEditor account={account} balance={balance} onDismiss={() => setPage(null)} />}
      {removing && (
        <AlertDialog title="Eliminare il trasferimento?" text={`${Money.format(removing.amountCents)} da ${nameOf(removing.fromId)} a ${nameOf(removing.toId)}.`}
          confirm="Elimina" confirmColor="var(--expenseText)" onDismiss={() => setRemoving(null)}
          onConfirm={() => { actions.deleteTransfer(removing).catch(failed); setRemoving(null) }} />
      )}
    </div>
  )
}

/** Epoch day of a moment, in local time. */
const dayOf = (millis: number) => {
  const d = new Date(millis)
  return epochOf(d.getFullYear(), d.getMonth() + 1, d.getDate())
}

function AccountEditor({ original, onDismiss }: { original: Account; onDismiss: () => void }) {
  const accounts = useAccounts()
  const custom = usePaymentMethods()
  const [name, setName] = useState(original.name)
  const [icon, setIcon] = useState(original.icon)
  const [color, setColor] = useState(original.color)
  const [methods, setMethods] = useState(accountMethods(original))
  const [balance, setBalance] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const isNew = original.id === 0
  // A method moves one account only: the ones already taken by another account can't be picked here.
  const taken = new Map(accounts.filter((a) => a.id !== original.id).flatMap((a) => accountMethods(a).map((k) => [k, a.name] as const)))
  const preview = { ...original, name, icon, color }

  const save = () => {
    const account = { ...preview, name: name.trim(), methods: methods.join(',') }
    if (isNew) Object.assign(account, { balanceCents: Money.parse(balance) ?? 0, balanceAt: Date.now() })
    onDismiss()
    actions.saveAccount(account).catch(failed)
  }

  return (
    <FullPage title={isNew ? 'Nuovo conto' : 'Modifica conto'} onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <div className="center" style={{ paddingTop: 4 }}>
          <AccountBadge account={preview} size={96} />
        </div>
        <FormLabel text="Nome" />
        <NoteField value={name} onChange={setName} placeholder="Es. Conto Intesa" />
        {isNew && (
          <>
            <FormLabel text="Quanto c'è adesso" />
            <AmountField value={balance} onChange={setBalance} />
          </>
        )}
        <FormLabel text="Metodi di pagamento" />
        <span className="t-caption" style={{ color: 'var(--inkSoft)', paddingBottom: 8 }}>
          Le spese e le entrate pagate così, registrate da adesso, aggiornano il saldo.
        </span>
        <div className="flow">
          {allChoices(custom).map((choice) => {
            const key = methodKey(choice)
            const owner = taken.get(key)
            const label = paymentLabel(choice, custom)
            return (
              <Pill key={key} text={owner ? `${label.label} · ${owner}` : label.label} selected={methods.includes(key)} appIcon={label.icon}
                color={argb(color)} style={owner ? { opacity: 0.45 } : undefined}
                onClick={() => {
                  if (owner) toast(`${label.label} è già nel conto ${owner}`)
                  else setMethods(methods.includes(key) ? methods.filter((k) => k !== key) : [...methods, key])
                }} />
            )
          })}
        </div>
        <FormLabel text="Icona" />
        <IconPicker selected={icon} color={argb(color)} onSelect={setIcon} />
        <FormLabel text="Colore" />
        <ColorSwatches colors={paletteAll} selected={color} onSelect={setColor} />
        <div style={{ height: 24 }} />
        <PrimaryButton text="Salva" icon="check" enabled={!!name.trim()} color={argb(color)} onClick={save} />
        {!isNew && (
          <Bouncy onClick={() => setConfirmDelete(true)} className="t-bodyStrong"
            style={{ color: 'var(--expenseText)', textAlign: 'center', padding: 14, borderRadius: 999 }}>Elimina conto</Bouncy>
        )}
      </div>
      {confirmDelete && (
        <AlertDialog title={`Eliminare ${original.name}?`} text="I movimenti restano; si cancellano i trasferimenti di questo conto."
          confirm="Elimina" confirmColor="var(--expenseText)" onDismiss={() => setConfirmDelete(false)}
          onConfirm={() => {
            setConfirmDelete(false)
            onDismiss()
            close('account')
            actions.deleteAccount(original).catch(failed)
          }} />
      )}
    </FullPage>
  )
}

function TransferEditor({ from: initial, onDismiss }: { from: Account; onDismiss: () => void }) {
  const accounts = useAccounts()
  const today = todayEpoch()
  const [fromId, setFromId] = useState(initial.id)
  const [toId, setToId] = useState(accounts.find((a) => a.id !== initial.id)?.id ?? 0)
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(today)
  const [note, setNote] = useState('')
  const [picking, setPicking] = useState(false)
  const cents = Money.parse(amount) ?? 0
  const otherDate = date !== today && date !== today - 1

  const save = () => {
    onDismiss()
    actions.saveTransfer({ id: 0, fromId, toId, amountCents: cents, date, note: note.trim(), createdAt: Date.now() }).catch(failed)
  }

  const picker = (selected: number, onSelect: (id: number) => void, other: number) => (
    <div className="flow">
      {accounts.map((a) => (
        <Pill key={a.id} text={a.name} selected={selected === a.id} appIcon={a.icon} color={argb(a.color)}
          style={a.id === other ? { opacity: 0.45 } : undefined}
          onClick={() => {
            if (a.id !== other) return onSelect(a.id)
            // Picking the other side's account swaps the two.
            setFromId(toId)
            setToId(fromId)
          }} />
      ))}
    </div>
  )

  return (
    <FullPage title="Trasferimento" onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <FormLabel text="Da" />
        {picker(fromId, setFromId, toId)}
        <FormLabel text="A" />
        {picker(toId, setToId, fromId)}
        <FormLabel text="Importo" />
        <AmountField value={amount} onChange={setAmount} />
        <FormLabel text="Data" />
        <div className="flow">
          <Pill text="Oggi" selected={date === today} onClick={() => setDate(today)} />
          <Pill text="Ieri" selected={date === today - 1} onClick={() => setDate(today - 1)} />
          <Pill text={otherDate ? Dates.long(date) : 'Altra data'} selected={otherDate} onClick={() => setPicking(true)} icon="calendar_month" />
        </div>
        <FormLabel text="Nota" />
        <NoteField value={note} onChange={setNote} placeholder="Es. prelievo al bancomat" />
        <span className="t-caption" style={{ color: 'var(--inkFaint)', paddingTop: 12 }}>
          Non è né una spesa né un'entrata: sposta solo i soldi da un conto all'altro.
        </span>
        <div style={{ height: 24 }} />
        <PrimaryButton text={`Trasferisci ${Money.format(cents)}`} icon="check" enabled={cents > 0 && fromId !== toId && toId !== 0} onClick={save} />
      </div>
      {picking && <DatePickerDialog value={date} onDismiss={() => setPicking(false)} onPick={(d) => { setDate(d); setPicking(false) }} />}
    </FullPage>
  )
}

function BalanceEditor({ account, balance, onDismiss }: { account: Account; balance: number; onDismiss: () => void }) {
  const [amount, setAmount] = useState(balance > 0 ? Money.toInput(balance) : '')
  const save = () => {
    onDismiss()
    actions.saveAccount({ ...account, balanceCents: Money.parse(amount) ?? 0, balanceAt: Date.now() }).catch(failed)
  }
  return (
    <FullPage title="Correggi saldo" onDismiss={onDismiss}>
      <div className="col" style={{ padding: '0 20px 32px', gap: 2 }}>
        <FormLabel text={`Quanto c'è davvero su ${account.name}`} />
        <AmountField value={amount} onChange={setAmount} />
        <span className="t-caption" style={{ color: 'var(--inkFaint)', paddingTop: 12 }}>
          Il saldo riparte da qui: contano solo i movimenti e i trasferimenti registrati da adesso.
        </span>
        <div style={{ height: 24 }} />
        <PrimaryButton text="Salva" icon="check" color={argb(account.color)} onClick={save} />
      </div>
    </FullPage>
  )
}
