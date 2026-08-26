export function InlineFieldError({
  field,
  fieldErrors,
}: {
  field: string
  fieldErrors?: { field: string; message: string }[]
}) {
  const err = fieldErrors?.find((e) => e.field === field)
  return err ? <span className="field-error-text">{err.message}</span> : null
}
