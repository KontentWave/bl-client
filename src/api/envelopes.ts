import { z } from "zod";

export const errorMapSchema = z
  .record(z.string(), z.array(z.string()))
  .default({});

export const metaSchema = z.record(z.string(), z.unknown()).default({});

export const successEnvelopeSchema = <T extends z.ZodTypeAny>(dataSchema: T) =>
  z.object({
    success: z.literal(true),
    code: z.string(),
    data: dataSchema,
    meta: metaSchema,
  });

export const failureEnvelopeSchema = z.object({
  success: z.literal(false),
  code: z.string(),
  message: z.string(),
  errors: errorMapSchema,
  meta: metaSchema,
});

export type SuccessEnvelope<T> = {
  success: true;
  code: string;
  data: T;
  meta: Record<string, unknown>;
};

export type FailureEnvelope = z.infer<typeof failureEnvelopeSchema>;

export function parseSuccessEnvelope<T>(
  dataSchema: z.ZodType<T>,
  payload: unknown,
): SuccessEnvelope<T> {
  return successEnvelopeSchema(dataSchema).parse(payload);
}

export function parseFailureEnvelope(payload: unknown): FailureEnvelope {
  return failureEnvelopeSchema.parse(payload);
}
