import { z } from "zod";

export type ErrorMap = Record<string, string[]>;

function normalizeErrorMap(errors: unknown): ErrorMap {
  if (Array.isArray(errors)) {
    return errors.reduce<ErrorMap>((accumulator, issue, index) => {
      const key =
        issue &&
        typeof issue === "object" &&
        "path" in issue &&
        Array.isArray(issue.path) &&
        issue.path.length > 0
          ? String(issue.path[0])
          : "general";

      const message =
        issue &&
        typeof issue === "object" &&
        "message" in issue &&
        typeof issue.message === "string"
          ? issue.message
          : typeof issue === "string"
            ? issue
            : `Request validation error #${index + 1}.`;

      accumulator[key] = [...(accumulator[key] ?? []), message];
      return accumulator;
    }, {});
  }

  if (!errors || typeof errors !== "object") {
    return {};
  }

  return Object.entries(errors).reduce<ErrorMap>(
    (accumulator, [key, value]) => {
      if (Array.isArray(value)) {
        accumulator[key] = value.map((entry) => String(entry));
        return accumulator;
      }

      if (typeof value === "string") {
        accumulator[key] = [value];
        return accumulator;
      }

      if (value != null) {
        accumulator[key] = [String(value)];
      }

      return accumulator;
    },
    {},
  );
}

export const errorMapSchema = z
  .unknown()
  .optional()
  .transform((value) => normalizeErrorMap(value));

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
