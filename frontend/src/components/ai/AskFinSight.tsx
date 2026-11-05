"use client";
import { useEffect, useRef, useState } from 'react';
import { aiApi, AiExplanation, ConversationMessage, conversationHistory, MAX_CONVERSATION_MESSAGES, MAX_QUESTION_LENGTH } from '@/lib/api/ai';
import { ApiError } from '@/lib/api/client';
import { useLanguage } from '@/lib/i18n/context';
import { phaseTwoCopy } from '@/lib/i18n/translations/phase-two';
import { LivePage } from '@/components/integration/LivePage';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { useAi } from './AiProvider';
import { ExplanationResult } from './AiExplanation';
import { useSession } from '@/components/auth/SessionProvider';

export function AskFinSight() {
  const { locale } = useLanguage(); const copy = phaseTwoCopy(locale); const ai = useAi(); const session = useSession();
  return <LivePage title={copy.ask}><Card padding="lg" className="space-y-3"><h1 className="text-xl font-semibold">{copy.ask}</h1><p>{copy.askHelp}</p><p className="text-sm">{copy.aiScope}</p><p className="text-sm">{copy.conversationHelp}</p></Card>
    {!session.can('FINANCIAL_DATA_READ') ? <p role="alert">{copy.permission}</p> : !ai?.available ? <Card padding="lg" className="space-y-3"><p role="status">{ai?.error ? copy.aiUnavailable : copy.disabledAi}</p><Button variant="secondary" onClick={ai?.retry}>{copy.refreshStatus}</Button></Card> : <Conversation key={locale}/>}</LivePage>;
}
interface Exchange { question: string; response: AiExplanation }
function Conversation() {
  const ai = useAi()!; const { locale } = useLanguage(); const copy = phaseTwoCopy(locale);
  const [question, setQuestion] = useState(''), [exchanges, setExchanges] = useState<Exchange[]>([]), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const maxLength = Math.min(MAX_QUESTION_LENGTH, ai.status?.maxQuestionLength || MAX_QUESTION_LENGTH);
  const maxMessages = Math.min(MAX_CONVERSATION_MESSAGES, ai.status?.maxConversationTurns || MAX_CONVERSATION_MESSAGES);
  const atLimit = exchanges.length * 2 >= maxMessages;
  const clear = () => { request.current?.abort(); setExchanges([]); setQuestion(''); setError(''); setBusy(false); };
  const send = async (event: React.FormEvent) => {
    event.preventDefault(); if (busy || atLimit || !ai.available) return;
    const text = question.trim(); if (!text) { setError(copy.questionRequired); return; }
    const controller = new AbortController(); request.current = controller; setBusy(true); setError('');
    const history: ConversationMessage[] = exchanges.flatMap(item => [{ role: 'user' as const, content: item.question }, { role: 'assistant' as const, content: item.response.summary }]);
    try {
      const response = await aiApi.explain({ type: 'ask', language: locale, question: text, conversation: conversationHistory(history) }, controller.signal);
      if (!controller.signal.aborted) { setExchanges(previous => [...previous, { question: text, response }]); setQuestion(''); }
    } catch (failure) { if (failure instanceof Error && failure.name === 'AbortError') return; setError(failure instanceof ApiError && failure.status === 429 ? copy.requestLimit : copy.aiUnavailable); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  };
  return <div className="space-y-5" data-testid="ask-conversation">
    {ai.status?.mode === 'simulated' && <Card padding="md" className="bg-amber-50 text-amber-950 space-y-2"><p className="font-semibold">{copy.simulated}</p><p className="text-sm">{copy.simulationHelp}</p></Card>}
    <div className="space-y-4" aria-live="polite" aria-relevant="additions">{exchanges.map((item, index) => <Card key={index} padding="lg" className="space-y-4"><section><h2 className="font-semibold">{copy.you}</h2><p className="whitespace-pre-wrap break-words">{item.question}</p></section><section><h2 className="font-semibold mb-3">{copy.assistant}</h2><ExplanationResult result={item.response}/></section></Card>)}</div>
    <Card padding="lg" className="space-y-4"><div className="flex justify-between items-center flex-wrap gap-3"><p className="text-sm">{copy.limit}: <bdi>{exchanges.length * 2} / {maxMessages}</bdi></p><Button variant="ghost" onClick={clear}>{copy.clear}</Button></div>
      {exchanges.length === 0 && <div className="flex flex-wrap gap-2">{[copy.supportedPrompt, copy.cashPrompt, copy.missingPrompt].map(prompt => <button type="button" key={prompt} className="border rounded-full px-3 py-2 text-sm text-start" disabled={busy} onClick={() => setQuestion(prompt)}>{prompt}</button>)}</div>}
      <form className="space-y-3" onSubmit={event => void send(event)}><label className="block font-medium">{copy.question}<textarea data-testid="ask-question" className="block w-full border rounded-lg p-3 mt-2 min-h-28 resize-y" value={question} onChange={event => setQuestion(event.target.value)} maxLength={maxLength} disabled={busy || atLimit} placeholder={copy.questionPlaceholder} required/></label><p className="text-xs"><bdi>{question.length} / {maxLength}</bdi> {copy.characters}</p>{atLimit && <p role="status">{copy.conversationLimit}</p>}{error && <p role="alert">{error}</p>}<Button type="submit" data-testid="ask-send" disabled={busy || atLimit || !question.trim()} isLoading={busy}>{busy ? copy.aiLoading : copy.send}</Button></form>
    </Card>
  </div>;
}
