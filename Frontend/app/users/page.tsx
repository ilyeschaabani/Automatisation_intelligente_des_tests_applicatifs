import { Header } from '@/components/header'
import { Sidebar } from '@/components/sidebar'
import { Card } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Plus, Edit2, Trash2, Shield } from 'lucide-react'

const users = [
  {
    id: 1,
    name: 'Ahmed Ben Ali',
    email: 'ahmed.benali@bankingcorp.com',
    role: 'Admin',
    status: 'Active',
    joinDate: '2024-01-15',
    lastLogin: '2024-02-11 14:20',
  },
  {
    id: 2,
    name: 'Fatima Karim',
    email: 'fatima.karim@bankingcorp.com',
    role: 'Test Manager',
    status: 'Active',
    joinDate: '2024-01-20',
    lastLogin: '2024-02-11 10:35',
  },
  {
    id: 3,
    name: 'Mohamed Hassan',
    email: 'mohamed.hassan@bankingcorp.com',
    role: 'QA Engineer',
    status: 'Active',
    joinDate: '2024-02-01',
    lastLogin: '2024-02-10 16:45',
  },
  {
    id: 4,
    name: 'Leila Souissi',
    email: 'leila.souissi@bankingcorp.com',
    role: 'QA Engineer',
    status: 'Active',
    joinDate: '2024-02-03',
    lastLogin: '2024-02-11 09:10',
  },
  {
    id: 5,
    name: 'Karim Bouzidi',
    email: 'karim.bouzidi@bankingcorp.com',
    role: 'DevOps Engineer',
    status: 'Inactive',
    joinDate: '2024-01-25',
    lastLogin: '2024-02-05 11:20',
  },
  {
    id: 6,
    name: 'Amina Belkebir',
    email: 'amina.belkebir@bankingcorp.com',
    role: 'Viewer',
    status: 'Active',
    joinDate: '2024-02-08',
    lastLogin: '2024-02-11 13:00',
  },
]

const roleConfig = {
  Admin: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-400',
  'Test Manager': 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-400',
  'QA Engineer': 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-400',
  'DevOps Engineer': 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  Viewer: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

const statusConfig = {
  Active: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-400',
  Inactive: 'bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-400',
}

export default function UsersPage() {
  return (
    <div className="flex min-h-screen bg-background">
      <Sidebar />

      <main className="flex-1 lg:ml-0 pt-16 lg:pt-0">
        <Header />

        <div className="p-6 max-w-7xl">
          {/* Page Header */}
          <div className="flex items-center justify-between mb-8">
            <div>
              <h1 className="text-3xl font-bold text-foreground">Users & Roles</h1>
              <p className="text-muted-foreground mt-1">
                Manage user access and permissions
              </p>
            </div>
            <Button className="bg-primary hover:bg-primary/90 text-primary-foreground gap-2">
              <Plus size={20} />
              Add User
            </Button>
          </div>

          {/* Summary */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <Card className="p-6">
              <p className="text-sm text-muted-foreground">Total Users</p>
              <p className="text-3xl font-bold text-foreground mt-2">6</p>
            </Card>
            <Card className="p-6">
              <p className="text-sm text-muted-foreground">Active Users</p>
              <p className="text-3xl font-bold text-foreground mt-2">5</p>
            </Card>
            <Card className="p-6">
              <p className="text-sm text-muted-foreground">Admin Users</p>
              <p className="text-3xl font-bold text-foreground mt-2">1</p>
            </Card>
          </div>

          {/* Users Table */}
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="border-b border-border">
                    <TableHead className="text-left">Name</TableHead>
                    <TableHead className="text-left">Email</TableHead>
                    <TableHead className="text-center">Role</TableHead>
                    <TableHead className="text-center">Status</TableHead>
                    <TableHead className="text-center">Join Date</TableHead>
                    <TableHead className="text-center">Last Login</TableHead>
                    <TableHead className="text-center">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {users.map((user) => (
                    <TableRow
                      key={user.id}
                      className="border-b border-border hover:bg-secondary/50"
                    >
                      <TableCell className="font-medium text-foreground">
                        <div className="flex items-center gap-3">
                          <div className="w-8 h-8 bg-gradient-to-br from-primary to-accent rounded-full flex items-center justify-center">
                            <span className="text-xs font-bold text-primary-foreground">
                              {user.name
                                .split(' ')
                                .map((n) => n[0])
                                .join('')}
                            </span>
                          </div>
                          {user.name}
                        </div>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {user.email}
                      </TableCell>
                      <TableCell className="text-center">
                        <Badge
                          variant="outline"
                          className={
                            roleConfig[user.role as keyof typeof roleConfig]
                          }
                        >
                          {user.role}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-center">
                        <Badge
                          variant="outline"
                          className={
                            statusConfig[user.status as keyof typeof statusConfig]
                          }
                        >
                          {user.status}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-center text-muted-foreground">
                        {user.joinDate}
                      </TableCell>
                      <TableCell className="text-center text-muted-foreground text-sm">
                        {user.lastLogin}
                      </TableCell>
                      <TableCell className="text-center">
                        <div className="flex items-center justify-center gap-2">
                          <Button variant="ghost" size="sm">
                            <Edit2 size={16} />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10"
                          >
                            <Trash2 size={16} />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </Card>

          {/* Role Permissions */}
          <Card className="p-6 mt-8">
            <div className="flex items-center gap-3 mb-6">
              <Shield className="text-primary" />
              <h2 className="text-lg font-bold text-foreground">
                Role Permissions
              </h2>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="text-left py-3 px-4 font-semibold text-foreground">
                      Permission
                    </th>
                    <th className="text-center py-3 px-4 font-semibold text-foreground">
                      Admin
                    </th>
                    <th className="text-center py-3 px-4 font-semibold text-foreground">
                      Test Manager
                    </th>
                    <th className="text-center py-3 px-4 font-semibold text-foreground">
                      QA Engineer
                    </th>
                    <th className="text-center py-3 px-4 font-semibold text-foreground">
                      Viewer
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {[
                    'Create Campaigns',
                    'Run Tests',
                    'View Results',
                    'Generate Reports',
                    'Manage Users',
                    'System Settings',
                  ].map((permission) => (
                    <tr key={permission} className="border-b border-border">
                      <td className="py-3 px-4 text-foreground">{permission}</td>
                      <td className="text-center py-3 px-4">
                        <span className="text-green-600 dark:text-green-400 font-bold">
                          ✓
                        </span>
                      </td>
                      <td className="text-center py-3 px-4">
                        <span className="text-green-600 dark:text-green-400 font-bold">
                          {['Create Campaigns', 'Run Tests', 'View Results', 'Generate Reports'].includes(
                            permission
                          )
                            ? '✓'
                            : '✗'}
                        </span>
                      </td>
                      <td className="text-center py-3 px-4">
                        <span className="text-green-600 dark:text-green-400 font-bold">
                          {['Run Tests', 'View Results', 'Generate Reports'].includes(permission)
                            ? '✓'
                            : '✗'}
                        </span>
                      </td>
                      <td className="text-center py-3 px-4">
                        <span className="text-green-600 dark:text-green-400 font-bold">
                          {permission === 'View Results' ? '✓' : '✗'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>
      </main>
    </div>
  )
}
