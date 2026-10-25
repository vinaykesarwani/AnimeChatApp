export function applyMessageEvent(messages, event) {
  if (!['CREATE', 'EDIT', 'DELETE'].includes(event.type)) return messages;
  const id = event.message?.id ?? event.messageId;
  if (id == null) return messages;
  const previous = messages.find(message => message.id === id);
  const replacement = event.type === 'DELETE'
    ? { ...previous, ...event.message, id, content: '[deleted]', deleted: true }
    : event.message;
  if (!replacement) return messages;
  const updated = previous ? messages.map(message => message.id === id ? replacement : message)
    : [...messages, replacement];
  return updated.map(message => message.replyTo?.id === id
    ? { ...message, replyTo: { ...message.replyTo, content: replacement.content, deleted: replacement.deleted } }
    : message).sort((left, right) => left.id - right.id);
}

export function mergeHistory(history, liveEvents) {
  return liveEvents.reduce(applyMessageEvent, history);
}