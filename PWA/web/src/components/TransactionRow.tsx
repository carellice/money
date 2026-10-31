import { animate, motion, useMotionValue, useTransform } from 'framer-motion'
import { useState, type CSSProperties, type ReactNode } from 'react'
import { Money } from '../../../shared/format.ts'
import { argb, payment, type Category, type Transaction } from '../../../shared/model.ts'
import { useStore } from '../lib/store.ts'
import { Ink } from '../lib/theme.ts'
import { Bouncy, CategoryBadge, Icon } from './ui.tsx'
import { spring } from './motion.ts'

export function TransactionRow({ tx, categories, onClick, showDate, style }: {
  tx: Transaction
  categories: Map<number, Category>
  onClick: () => void
  showDate?: string
  style?: CSSProperties
}) {
  const members = useStore((s) => s.data?.members)
  const category = categories.get(tx.categoryId)
  const sub = tx.subcategoryId != null ? categories.get(tx.subcategoryId) : undefined
  const member = tx.memberId != null ? members?.find((m) => m.id === tx.memberId) : undefined
  const title = tx.note.trim() ? tx.note : sub?.name ?? category?.name ?? 'Movimento'
  const subtitle = [
    member?.name,
    tx.note.trim() ? sub?.name ?? category?.name ?? '' : sub ? category?.name ?? '' : undefined,
    payment(tx.payment).label,
    showDate,
  ].filter((s): s is string => !!s && !!s.trim()).join(' · ')
  const income = tx.type === 'INCOME'
  return (
    <Bouncy pressedScale={0.97} haptics={false} onClick={onClick} className="row" style={{ padding: '8px 0', gap: 14, ...style }}>
      <CategoryBadge icon={category?.icon ?? 'more'} color={category ? argb(category.color) : 'var(--surfaceHigh)'} size={46} seed={tx.id} />
      <div className="col flex1">
        <span className="t-bodyStrong ellipsis" style={{ color: 'var(--ink)' }}>{title}</span>
        <div className="row" style={{ minWidth: 0 }}>
          {tx.recurringId != null && <Icon name="autorenew" size={13} color="var(--inkFaint)" style={{ marginRight: 2 }} />}
          {tx.receiptPath != null && <Icon name="receipt_long" size={13} color="var(--inkFaint)" style={{ marginRight: 2 }} />}
          <span className="t-caption ellipsis" style={{ color: 'var(--inkSoft)' }}>{subtitle}</span>
        </div>
      </div>
      <span className="t-amount" style={{ color: income ? 'var(--incomeText)' : 'var(--ink)', whiteSpace: 'nowrap' }}>
        {Money.format(income ? tx.amountCents : -tx.amountCents, true)}
      </span>
    </Bouncy>
  )
}

/** SwipeToDismissBox from right to left, with the delete background behind the row. */
export function SwipeRow({ onDelete, children }: { onDelete: () => void; children: ReactNode }) {
  const x = useMotionValue(0)
  const [active, setActive] = useState(false)
  const [gone, setGone] = useState(false)
  const iconOpacity = useTransform(x, [0, -40], [0.6, 1])
  return (
    <motion.div style={{ position: 'relative', overflow: 'hidden' }} animate={gone ? { height: 0, opacity: 0 } : undefined} transition={{ duration: 0.25 }}>
      <div className="row" style={{
        position: 'absolute', inset: 0, borderRadius: 24, padding: '0 24px', justifyContent: 'flex-end',
        background: active ? 'var(--expense)' : 'var(--surfaceHigh)', transition: 'background-color 0.2s',
      }}>
        <motion.div style={{ opacity: iconOpacity }}><Icon name="delete" color={active ? Ink : 'var(--inkSoft)'} label="Elimina" /></motion.div>
      </div>
      <motion.div
        drag="x"
        dragDirectionLock
        dragConstraints={{ left: 0, right: 0 }}
        dragElastic={{ left: 1, right: 0 }}
        style={{ x, position: 'relative', background: 'var(--bg)', touchAction: 'pan-y' }}
        onDrag={(_, info) => setActive(info.offset.x < -window.innerWidth * 0.25)}
        onDragEnd={(_, info) => {
          if (info.offset.x < -Math.min(window.innerWidth, 520) * 0.25 || info.velocity.x < -800) {
            animate(x, -Math.min(window.innerWidth, 520), { ...spring(1, 400), onComplete: () => { setGone(true); onDelete() } })
          } else {
            setActive(false)
            animate(x, 0, spring(0.7, 400))
          }
        }}
      >
        {children}
      </motion.div>
    </motion.div>
  )
}
