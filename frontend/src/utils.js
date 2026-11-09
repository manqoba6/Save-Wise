const pad = (n) => String(n).padStart(2, '0');

/** Today as YYYY-MM-DD in the user's own time zone (what the date inputs and the API use). */
export function today() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Whole days from today to a YYYY-MM-DD date. Negative when the date has passed. */
export function daysUntil(isoDate) {
  const start = new Date(); start.setHours(0, 0, 0, 0);
  return Math.round((new Date(`${isoDate}T00:00:00`) - start) / 86400000);
}

/** Roughly what a recurring amount comes to in a month, so schedules of different frequencies can be added up. */
export function monthlyEquivalent(amount, frequency) {
  const a = Number(amount);
  switch (frequency) {
    case 'DAILY': return (a * 365) / 12;
    case 'WEEKLY': return (a * 52) / 12;
    case 'YEARLY': return a / 12;
    default: return a;
  }
}

/** Saves a Blob to the user's computer under the given file name. */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
