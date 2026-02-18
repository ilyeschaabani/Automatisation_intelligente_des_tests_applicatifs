import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Bell, Lock, Palette, Zap } from 'lucide-react'

export default function SettingsPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-2xl">
          {/* Page Header */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-foreground">Settings</h1>
            <p className="text-muted-foreground mt-1">
              Manage your platform preferences and integrations
            </p>
          </div>

          {/* General Settings */}
          <Card className="p-6 mb-6">
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 bg-secondary rounded-lg">
                <Palette className="text-primary" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-foreground">
                  General Settings
                </h2>
                <p className="text-sm text-muted-foreground">
                  Configure basic platform settings
                </p>
              </div>
            </div>

            <div className="space-y-6">
              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Platform Name
                </label>
                <Input defaultValue="TestAuto" />
              </div>

              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Organization
                </label>
                <Input defaultValue="Banking Corp Inc." />
              </div>

              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Email
                </label>
                <Input defaultValue="admin@bankingcorp.com" type="email" />
              </div>

              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Theme
                </label>
                <Select defaultValue="auto">
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="light">Light</SelectItem>
                    <SelectItem value="dark">Dark</SelectItem>
                    <SelectItem value="auto">Auto (System)</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <Button className="bg-primary hover:bg-primary/90 text-primary-foreground">
                Save Changes
              </Button>
            </div>
          </Card>

          {/* Notifications */}
          <Card className="p-6 mb-6">
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 bg-secondary rounded-lg">
                <Bell className="text-primary" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-foreground">
                  Notifications
                </h2>
                <p className="text-sm text-muted-foreground">
                  Manage how you receive alerts
                </p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="flex items-center justify-between p-4 bg-secondary rounded-lg">
                <div>
                  <p className="font-semibold text-foreground">
                    Test Failure Alerts
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Notify when tests fail
                  </p>
                </div>
                <input type="checkbox" defaultChecked className="w-5 h-5" />
              </div>

              <div className="flex items-center justify-between p-4 bg-secondary rounded-lg">
                <div>
                  <p className="font-semibold text-foreground">
                    Campaign Completion
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Notify when campaigns finish
                  </p>
                </div>
                <input type="checkbox" defaultChecked className="w-5 h-5" />
              </div>

              <div className="flex items-center justify-between p-4 bg-secondary rounded-lg">
                <div>
                  <p className="font-semibold text-foreground">
                    Weekly Summary
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Send weekly test summary email
                  </p>
                </div>
                <input type="checkbox" className="w-5 h-5" />
              </div>

              <Button className="bg-primary hover:bg-primary/90 text-primary-foreground w-full">
                Update Preferences
              </Button>
            </div>
          </Card>

          {/* Security */}
          <Card className="p-6 mb-6">
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 bg-secondary rounded-lg">
                <Lock className="text-primary" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-foreground">Security</h2>
                <p className="text-sm text-muted-foreground">
                  Manage security and access control
                </p>
              </div>
            </div>

            <div className="space-y-6">
              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Current Password
                </label>
                <Input type="password" />
              </div>

              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  New Password
                </label>
                <Input type="password" />
              </div>

              <div>
                <label className="text-sm font-semibold text-foreground block mb-2">
                  Confirm Password
                </label>
                <Input type="password" />
              </div>

              <Button className="bg-destructive hover:bg-destructive/90 text-destructive-foreground">
                Change Password
              </Button>
            </div>
          </Card>

          {/* CI/CD Integration */}
          <Card className="p-6">
            <div className="flex items-center gap-4 mb-6">
              <div className="p-3 bg-secondary rounded-lg">
                <Zap className="text-primary" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-foreground">
                  CI/CD Integration
                </h2>
                <p className="text-sm text-muted-foreground">
                  Configure pipeline integrations
                </p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="p-4 border border-border rounded-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-semibold text-foreground">Jenkins</p>
                    <p className="text-sm text-muted-foreground">
                      Connected
                    </p>
                  </div>
                  <Button
                    variant="outline"
                    className="text-destructive hover:bg-destructive/10 bg-transparent"
                  >
                    Disconnect
                  </Button>
                </div>
              </div>

              <div className="p-4 border border-border rounded-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-semibold text-foreground">GitLab CI</p>
                    <p className="text-sm text-muted-foreground">
                      Not connected
                    </p>
                  </div>
                  <Button className="bg-primary hover:bg-primary/90 text-primary-foreground">
                    Connect
                  </Button>
                </div>
              </div>

              <div className="p-4 border border-border rounded-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-semibold text-foreground">GitHub Actions</p>
                    <p className="text-sm text-muted-foreground">
                      Not connected
                    </p>
                  </div>
                  <Button className="bg-primary hover:bg-primary/90 text-primary-foreground">
                    Connect
                  </Button>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}
