export type AssignmentStatus = 'CONFIRMED' | 'PROBABLE' | 'POTENTIAL'

export interface TeamMember {
  id: number
  firstName: string
  lastName: string
  email?: string
  country?: string
  city?: string
}

export interface Customer {
  id: number
  name: string
  country?: string
  city?: string
}

export interface Assignment {
  id: number
  teamMemberId: number
  customerId: number
  usagePercent: number
  status: AssignmentStatus
  month: string
}

export interface AssignmentUsage {
  assignmentId: number
  customerId: number
  customerName: string
  usage: number
  status: AssignmentStatus
}

export interface MonthUsage {
  total: number
  assignments: AssignmentUsage[]
}

export interface TeamMemberUsage {
  teamMemberId: number
  teamMemberName: string
  country: string
  months: Record<string, MonthUsage>
}

export interface LogoSearchResult {
  url: string
  thumbnailUrl: string
}

export type Role = 'VIEW' | 'VIEW_WRITE' | 'ADMIN'

export interface CurrentUser {
  email: string
  role: Role
  mustChangePassword: boolean
}

export interface AppUser {
  id: number
  email: string
  role: Role
  enabled: boolean
  mustChangePassword: boolean
  createdAt: string
}
