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
	onSubmit: (event: React.FormEvent<HTMLFormElement>) => void
	children: React.ReactNode
}

export function FormDialog({
	open,
	onOpenChange,
	title,
	description,
	submitLabel = 'Save',
	isSubmitting = false,
	onSubmit,
	children,
}: FormDialogProps) {
	return (
		<Dialog open={open} onOpenChange={onOpenChange}>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
					{description ? <DialogDescription>{description}</DialogDescription> : null}
				</DialogHeader>
				<form className="space-y-4" onSubmit={onSubmit}>
					{children}
					<DialogFooter>
						<Button type="submit" disabled={isSubmitting}>
							{isSubmitting ? 'Saving...' : submitLabel}
						</Button>
					</DialogFooter>
				</form>
			</DialogContent>
		</Dialog>
	)
}
