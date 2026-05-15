import { NextRequest, NextResponse } from 'next/server'

const BACKEND_URL = process.env.MS_GESTION_URL || 'http://localhost:8082'

export async function PUT(
  request: NextRequest,
  {
    params,
  }: {
    params: Promise<{ id: string; campaignId: string }>
  }
) {
  try {
    const { id: projectId, campaignId } = await params

    const response = await fetch(
      `${BACKEND_URL}/api/projects/${projectId}/campaigns/${campaignId}/stop`,
      {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
        },
      }
    )

    const data = await response.json()

    if (!response.ok) {
      return NextResponse.json(data, { status: response.status })
    }

    return NextResponse.json(data, { status: response.status })
  } catch (error) {
    console.error('Error stopping campaign:', error)
    return NextResponse.json(
      { message: 'Failed to stop campaign' },
      { status: 500 }
    )
  }
}
