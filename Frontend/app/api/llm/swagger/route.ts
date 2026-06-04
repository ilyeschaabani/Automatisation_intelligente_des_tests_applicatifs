import { NextResponse } from 'next/server'

const MS_GESTION_URL =
  process.env.TEST_MANAGEMENT_SERVICE_URL ??
  process.env.PROJECTS_SERVICE_URL ??
  'http://localhost:8082'

/**
 * Proxy to ms_gestion's OpenAPI spec.
 * GET /api/llm/swagger?schema=TestCaseResponse
 *   → fetches /v3/api-docs from ms_gestion
 *   → returns the properties of the requested schema (fields, types, enums)
 */
export async function GET(request: Request) {
  const url = new URL(request.url)
  const schemaName = url.searchParams.get('schema')

  try {
    const res = await fetch(`${MS_GESTION_URL}/v3/api-docs`, { cache: 'no-store' })
    if (!res.ok) {
      return NextResponse.json({ error: 'Swagger not available' }, { status: 503 })
    }

    const spec = await res.json() as OpenApiSpec

    if (!schemaName) {
      // Return list of all schema names
      const names = Object.keys(spec.components?.schemas ?? {})
      return NextResponse.json({ schemas: names })
    }

    const schema = spec.components?.schemas?.[schemaName]
    if (!schema) {
      return NextResponse.json({ error: `Schema '${schemaName}' not found` }, { status: 404 })
    }

    const required = new Set<string>(schema.required ?? [])
    const fields: SchemaField[] = Object.entries(schema.properties ?? {}).map(([name, prop]) => ({
      name,
      type: prop.type ?? (prop.$ref ? resolveRef(prop.$ref) : 'object'),
      format: prop.format,
      enumValues: prop.enum ?? [],
      required: required.has(name),
      description: prop.description,
    }))

    return NextResponse.json({ schemaName, fields })
  } catch (err) {
    return NextResponse.json({ error: 'Failed to fetch Swagger spec' }, { status: 500 })
  }
}

function resolveRef(ref: string): string {
  // "#/components/schemas/TestCaseResponse" → "TestCaseResponse"
  return ref.split('/').pop() ?? ref
}

// ── Types ─────────────────────────────────────────────────────────────────────

interface OpenApiSpec {
  components?: {
    schemas?: Record<string, SchemaObject>
  }
}

interface SchemaObject {
  type?: string
  required?: string[]
  properties?: Record<string, PropertyObject>
}

interface PropertyObject {
  type?: string
  format?: string
  enum?: string[]
  description?: string
  $ref?: string
}

export interface SchemaField {
  name: string
  type: string
  format?: string
  enumValues: string[]
  required: boolean
  description?: string
}
