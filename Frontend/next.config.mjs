/** @type {import('next').NextConfig} */
const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },

  async rewrites() {
    return [
      {
        source: '/api-scanner/:path*',
        destination: 'http://localhost:8099/:path*',
      },
    ]
  },
}

export default nextConfig
