/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },

  experimental: {
    serverActions: {
      bodySizeLimit: '500mb',
    },
  },

  async rewrites() {
    return [
      {
        source: '/api-scanner/:path*',
        destination: 'http://localhost:8099/:path*',
      },
      {
        source: '/api/functional-evaluation/:path*',
        destination: 'http://localhost:8083/api/functional-evaluation/:path*',
      },
      {
        source: '/api/intelligence/ux-evaluations/:path*',
        destination: 'http://localhost:8083/api/intelligence/ux-evaluations/:path*',
      },
    ]
  },
}

export default nextConfig
