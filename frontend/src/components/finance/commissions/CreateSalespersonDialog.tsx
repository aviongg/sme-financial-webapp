"use client";

import React, { useState } from 'react';
import { Dialog } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { commissionCopy } from '@/lib/i18n/translations/commission';
import { createSalesperson, type Salesperson, type SalespersonCreateInput } from '@/lib/api/dev-a/commission';
import { ApiError } from '@/lib/api/client';

interface CreateSalespersonDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (salesperson: Salesperson) => void;
  locale: string;
}

export function CreateSalespersonDialog({
  isOpen,
  onClose,
  onSuccess,
  locale,
}: CreateSalespersonDialogProps) {
  const copy = commissionCopy(locale);

  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [notes, setNotes] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resetForm = () => {
    setDisplayName('');
    setEmail('');
    setPhone('');
    setNotes('');
    setError(null);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!displayName.trim()) {
      setError(copy.salesperson.displayName + ' is required');
      return;
    }

    if (!email.trim() || !email.includes('@')) {
      setError(copy.salesperson.email + ' must be a valid email');
      return;
    }

    setLoading(true);
    try {
      const input: SalespersonCreateInput = {
        displayName: displayName.trim(),
        email: email.trim(),
        phone: phone.trim() ? phone.trim() : undefined,
        notes: notes.trim() ? notes.trim() : undefined,
      };

      const created = await createSalesperson(input);
      resetForm();
      onSuccess(created);
      onClose();
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError(copy.common.error);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog
      isOpen={isOpen}
      onClose={handleClose}
      title={copy.salesperson.createTitle}
      description={copy.salesperson.createIntro}
      maxWidth="md"
    >
      <form onSubmit={handleSubmit} className="space-y-4 pt-2">
        {error && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-950/30 dark:text-red-400"
          >
            {error}
          </div>
        )}

        <Input
          label={copy.salesperson.displayName}
          type="text"
          required
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          placeholder={copy.salesperson.displayNamePlaceholder}
          disabled={loading}
        />

        <Input
          label={copy.salesperson.email}
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder={copy.salesperson.emailPlaceholder}
          disabled={loading}
        />

        <Input
          label={copy.salesperson.phone}
          type="tel"
          isOptional
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          placeholder={copy.salesperson.phonePlaceholder}
          disabled={loading}
        />

        <Input
          label={copy.salesperson.notes}
          type="text"
          isOptional
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder={copy.salesperson.notesPlaceholder}
          disabled={loading}
        />

        <div className="flex justify-end gap-3 pt-4 border-t border-slate-100 dark:border-slate-800">
          <Button type="button" variant="secondary" onClick={handleClose} disabled={loading}>
            {copy.common.cancel}
          </Button>
          <Button type="submit" disabled={loading} isLoading={loading}>
            {copy.common.save}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
