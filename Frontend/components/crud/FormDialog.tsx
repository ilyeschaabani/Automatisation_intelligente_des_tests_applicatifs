'use client'

import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

type FormDialogProps = {
	open: boolean
	onOpenChange: (open: boolean) => void
	title: string
	description?: string
	submitLabel?: string
	isSubmitting?: boolean
	// When true, submit button will be disabled even if not submitting
	disableSubmit?: boolean
	// Size variant: 'default' (32rem), 'large' (56rem), or 'xl' (72rem)
	size?: 'default' | 'large' | 'xl'
	onSubmit: (event: React.FormEvent<HTMLFormElement>) => void
	children: React.ReactNode
}

const sizeClasses: Record<'default' | 'large' | 'xl', string> = {
	default: 'max-w-lg',
	large: 'max-w-4xl',
	xl: 'max-w-6xl',
}

export function FormDialog({
	open,
	onOpenChange,
	title,
	description,
	submitLabel = 'Save',
	isSubmitting = false,
	disableSubmit = false,
	size = 'default',
	onSubmit,
	children,
}: FormDialogProps) {
	const contentClass = sizeClasses[size]
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent className={contentClass}>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					{description ? <DialogDescription>{description}</DialogDescription> : null}
				</DialogHeader>
				<form className="space-y-4" onSubmit={onSubmit}>
					{children}
					<DialogFooter>
						<Button type="submit" disabled={isSubmitting || disableSubmit}>
							{isSubmitting ? 'Saving...' : submitLabel}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	)
}
