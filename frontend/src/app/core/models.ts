/* Tipos generados a partir del contrato OpenAPI de Vecindad. */

export interface UpdateWorkOrderStatusRequest {
  status: "PENDING" | "ASSIGNED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  actualCost?: number;
  completionNotes?: string;
}

export interface WorkOrder {
  id?: string;
  orderNumber?: string;
  assetId?: string;
  planId?: string;
  supplierId?: string;
  type?: "PREVENTIVE" | "CORRECTIVE" | "EMERGENCY" | "IMPROVEMENT";
  title?: string;
  description?: string;
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  status?: "PENDING" | "ASSIGNED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  estimatedCost?: number;
  actualCost?: number;
  createdBy?: string;
  assignedTo?: string;
  scheduledStart?: string;
  scheduledEnd?: string;
  startedAt?: string;
  completedAt?: string;
  completionNotes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type PetSpecies = "PERRO" | "GATO" | "AVE" | "OTRO";
export type PetStatus = "ACTIVE" | "INACTIVE";

export interface PetRequest {
  name: string;
  species?: PetSpecies;
  breed?: string;
  color?: string;
  birthDate?: string;
  weightKg?: number;
  microchip?: string;
  vaccinated?: boolean;
  vaccineExpiresOn?: string;
  notes?: string;
  status?: PetStatus;
  unitId?: string;
}

export interface Pet {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  name?: string;
  species?: PetSpecies;
  breed?: string;
  color?: string;
  birthDate?: string;
  weightKg?: number;
  microchip?: string;
  vaccinated?: boolean;
  vaccineExpiresOn?: string;
  notes?: string;
  status?: PetStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface VehicleRequest {
  typeCode: string;
  plate?: string;
  brand?: string;
  model?: string;
  color?: string;
  modelYear?: number;
  ownerPersonId?: string;
  unitId?: string;
  parkingSpaceId?: string;
  status?: "AUTHORIZED" | "BLOCKED";
}

export interface Vehicle {
  id?: string;
  typeCode?: string;
  plate?: string;
  brand?: string;
  model?: string;
  color?: string;
  modelYear?: number;
  ownerPersonId?: string;
  unitId?: string;
  unitIdentifier?: string;
  parkingSpaceId?: string;
  status?: "AUTHORIZED" | "BLOCKED";
  presence?: "INSIDE" | "OUTSIDE";
  primary?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface TypeRequest {
  name: string;
  active?: boolean;
  requiresPlate?: boolean;
}

export interface VehicleType {
  code?: string;
  name?: string;
  active?: boolean;
  requiresPlate?: boolean;
}

export interface LimitRequest {
  max: number;
}

export interface VehicleLimit {
  typeCode?: string;
  maxPerUnit?: number;
}

export interface CreateSupplierRequest {
  taxId: string;
  name: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  category: string;
  rating?: number;
  notes?: string;
}

export interface Supplier {
  id?: string;
  taxId?: string;
  name?: string;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  category?: string;
  rating?: number;
  isActive?: boolean;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PermissionChangeRequest {
  granted: boolean;
}

export interface RoleView {
  role?: string;
  permissions?: string[];
  editable?: boolean;
}

export interface PersonRequest {
  documentType?: string;
  documentNumber?: string;
  fullName: string;
  email?: string;
  phone?: string;
}

export interface Person {
  id?: string;
  documentType?: string;
  documentNumber?: string;
  fullName?: string;
  email?: string;
  phone?: string;
  userId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LinkUserRequest {
  email: string;
}

export interface PropertyRequest {
  type: "APARTAMENTO" | "CASA" | "LOCAL" | "PARQUEADERO" | "DEPOSITO" | "OTRO";
  identifier: string;
  unitNumber?: string;
  tower?: string;
  floorNumber?: number;
  coefficient?: number;
  areaM2?: number;
  status?: "ACTIVE" | "INACTIVE";
}

export interface PropertyUnit {
  id?: string;
  type?: "APARTAMENTO" | "CASA" | "LOCAL" | "PARQUEADERO" | "DEPOSITO" | "OTRO";
  identifier?: string;
  unitNumber?: string;
  tower?: string;
  floorNumber?: number;
  coefficient?: number;
  areaM2?: number;
  status?: "ACTIVE" | "INACTIVE";
  createdAt?: string;
  updatedAt?: string;
  deletedAt?: string;
  deletedBy?: string;
}

export interface AssignRequest {
  userId: string;
}

export interface Pqr {
  id?: string;
  ticketNumber?: string;
  unitId?: string;
  personId?: string;
  userId?: string;
  category?: "PETICION" | "QUEJA" | "RECLAMO" | "SUGERENCIA" | "CONVIVENCIA_RUIDO" | "CONVIVENCIA_MASCOTAS" | "CONVIVENCIA_GENERAL" | "OTRO";
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  status?: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | "REJECTED";
  title?: string;
  description?: string;
  isAnonymous?: boolean;
  assignedTo?: string;
  slaTargetHours?: number;
  slaDueAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  resolutionNotes?: string;
  createdAt?: string;
  updatedAt?: string;
  overdue?: boolean;
}

export interface FeatureUpdateRequest {
  enabled: boolean;
}

export interface UpdateSettingsRequest {
  emailEnabled?: boolean;
  pushEnabled?: boolean;
  whatsappEnabled?: boolean;
  senderDisplayName: string;
}

export interface TenantNotificationSettings {
  id?: string;
  emailEnabled?: boolean;
  pushEnabled?: boolean;
  whatsappEnabled?: boolean;
  senderDisplayName?: string;
  updatedAt?: string;
}

export interface CreateAssetRequest {
  name: string;
  category: string;
  location?: string;
  serialNumber?: string;
  brandModel?: string;
  installationDate?: string;
  warrantyExpiration?: string;
  status?: "OPERATIONAL" | "DEGRADED" | "OUT_OF_SERVICE" | "RETIRED";
}

export interface MaintenanceAsset {
  id?: string;
  name?: string;
  category?: string;
  location?: string;
  serialNumber?: string;
  brandModel?: string;
  installationDate?: string;
  warrantyExpiration?: string;
  status?: "OPERATIONAL" | "DEGRADED" | "OUT_OF_SERVICE" | "RETIRED";
  createdAt?: string;
  updatedAt?: string;
}

export interface Incident {
  id?: string;
  category?: "DANO" | "SEGURIDAD" | "RUIDO" | "VEHICULO" | "VISITANTE" | "INFRAESTRUCTURA" | "EMERGENCIA" | "OTRO";
  description?: string;
  location?: string;
  occurredAt?: string;
  reportedBy?: string;
  assignedTo?: string;
  status?: "OPEN" | "IN_REVIEW" | "CLOSED";
  resolution?: string;
  closedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CommonAreaRequest {
  name: string;
  description?: string;
  capacity?: number;
  requiresPayment?: boolean;
  pricePerSlot?: number;
  depositAmount?: number;
  requiresApproval?: boolean;
  openingTime: LocalTime;
  closingTime: LocalTime;
  slotDurationMinutes?: number;
  maxSlotsPerReservation?: number;
  maxActiveReservationsPerUnit?: number;
  minAdvanceHours?: number;
  maxAdvanceDays?: number;
  cancellationDeadlineHours?: number;
  terms?: string;
  approvalStartTime?: LocalTime;
  approvalEndTime?: LocalTime;
}

export interface LocalTime {
  hour?: number;
  minute?: number;
  second?: number;
  nano?: number;
}

export interface CommonArea {
  id?: string;
  name?: string;
  description?: string;
  capacity?: number;
  requiresPayment?: boolean;
  pricePerSlot?: number;
  depositAmount?: number;
  requiresApproval?: boolean;
  openingTime?: LocalTime;
  closingTime?: LocalTime;
  slotDurationMinutes?: number;
  maxSlotsPerReservation?: number;
  maxActiveReservationsPerUnit?: number;
  minAdvanceHours?: number;
  maxAdvanceDays?: number;
  cancellationDeadlineHours?: number;
  terms?: string;
  approvalStartTime?: LocalTime;
  approvalEndTime?: LocalTime;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SettingsRequest {
  allocationOrder: Array<"ORDINARY" | "EXTRAORDINARY" | "INTEREST" | "FINE" | "OTHER">;
  oldestFirst?: boolean;
  interestEnabled?: boolean;
  interestMonthlyRate: number;
  graceDays?: number;
  blockReservationsWhenOverdue?: boolean;
  overdueDaysForBlock?: number;
}

export interface BillingSettings {
  allocationOrder?: Array<"ORDINARY" | "EXTRAORDINARY" | "INTEREST" | "FINE" | "OTHER">;
  oldestFirst?: boolean;
  interestEnabled?: boolean;
  interestMonthlyRate?: number;
  graceDays?: number;
  blockReservationsWhenOverdue?: boolean;
  overdueDaysForBlock?: number;
}

export interface ReconciliationSettings {
  autoThreshold?: number;
  ambiguityGap?: number;
  autoApply?: boolean;
}

export interface ProfileRequest {
  name: string;
  bankName?: string;
  dateColumn: string;
  amountColumn?: string;
  creditColumn?: string;
  debitColumn?: string;
  descriptionColumn?: string;
  referenceColumn?: string;
  counterpartyColumn?: string;
  transactionIdColumn?: string;
  dateFormat?: string;
  decimalSeparator?: string;
}

export interface ImportProfile {
  id?: string;
  name?: string;
  bankName?: string;
  dateColumn?: string;
  amountColumn?: string;
  creditColumn?: string;
  debitColumn?: string;
  descriptionColumn?: string;
  referenceColumn?: string;
  counterpartyColumn?: string;
  transactionIdColumn?: string;
  dateFormat?: string;
  decimalSeparator?: string;
  active?: boolean;
}

export interface WalkInRequest {
  unitId: string;
  visitorName: string;
  documentNumber?: string;
  phone?: string;
  plate?: string;
  peopleCount?: number;
  note?: string;
}

export interface Visit {
  id?: string;
  invitationId?: string;
  unitId?: string;
  unitIdentifier?: string;
  visitorName?: string;
  documentNumber?: string;
  phone?: string;
  plate?: string;
  peopleCount?: number;
  source?: string;
  status?: "PENDING_AUTH" | "AUTHORIZED" | "REJECTED" | "INSIDE" | "LEFT";
  requestedAt?: string;
  decidedAt?: string;
  decisionNote?: string;
  entryTime?: string;
  exitTime?: string;
  note?: string;
}

export interface QrRequest {
  token: string;
}

export interface Invitation {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  visitorName?: string;
  documentNumber?: string;
  phone?: string;
  plate?: string;
  peopleCount?: number;
  validFrom?: string;
  validTo?: string;
  maxEntries?: number;
  usedCount?: number;
  status?: string;
  notes?: string;
  createdAt?: string;
}

export interface QrValidation {
  valid?: boolean;
  reason?: string;
  invitation?: Invitation;
}

export interface AddMemberRequest {
  email: string;
  fullName: string;
  role: string;
}

export interface MemberView {
  userId?: string;
  email?: string;
  fullName?: string;
  userStatus?: "INVITED" | "ACTIVE" | "DISABLED";
  roleCode?: string;
  status?: "ACTIVE" | "SUSPENDED" | "REVOKED";
  grantedAt?: string;
  lastActivityAt?: string;
}

export interface CreateContractRequest {
  contractNumber: string;
  title: string;
  description?: string;
  startDate: string;
  endDate: string;
  monthlyAmount?: number;
  totalAmount?: number;
  hasInsurancePolicy?: boolean;
  insurancePolicyNumber?: string;
  insuranceExpirationDate?: string;
}

export interface SupplierContract {
  id?: string;
  supplierId?: string;
  contractNumber?: string;
  title?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  monthlyAmount?: number;
  totalAmount?: number;
  hasInsurancePolicy?: boolean;
  insurancePolicyNumber?: string;
  insuranceExpirationDate?: string;
  status?: "ACTIVE" | "EXPIRED" | "TERMINATED" | "CANCELLED";
  createdAt?: string;
  updatedAt?: string;
}

export interface RejectRequest {
  reason?: string;
}

export interface Reservation {
  id?: string;
  commonAreaId?: string;
  unitId?: string;
  personId?: string;
  createdBy?: string;
  startTime?: string;
  endTime?: string;
  status?: "PENDING_PAYMENT" | "PENDING_APPROVAL" | "APPROVED" | "REJECTED" | "CANCELLED" | "COMPLETED";
  totalPrice?: number;
  depositAmount?: number;
  paymentMethod?: "NONE" | "CASH" | "WOMPI" | "BANK_TRANSFER";
  paymentReference?: string;
  rejectionReason?: string;
  cancellationReason?: string;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CancelRequest {
  reason?: string;
}

export interface WompiData {
  transaction?: WompiTransaction;
}

export interface WompiEvent {
  event?: string;
  data?: WompiData;
  environment?: string;
  signature?: WompiSignature;
  timestamp?: number;
  sent_at?: string;
}

export interface WompiFee {
  amount_in_cents?: number;
}

export interface WompiSignature {
  properties?: string[];
  checksum?: string;
}

export interface WompiTransaction {
  id?: string;
  reference?: string;
  currency?: string;
  status?: string;
  fee?: WompiFee;
  amount_in_cents?: number;
  payment_method_type?: string;
  payment_method?: Record<string, Record<string, unknown>>;
  status_message?: string;
}

export interface RelationRequest {
  personId: string;
  type: "OWNER" | "TENANT" | "RESIDENT";
  startDate?: string;
  /** Inmueble seleccionado en el formulario; se envía como parte de la ruta. */
  unitId?: string;
}

export interface UnitRelation {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  personId?: string;
  personName?: string;
  type?: "OWNER" | "TENANT" | "RESIDENT";
  startDate?: string;
  endDate?: string;
  status?: string;
}

export interface MessageRequest {
  message: string;
  isInternal?: boolean;
}

export interface PqrMessage {
  id?: string;
  pqrId?: string;
  senderUserId?: string;
  senderName?: string;
  isInternal?: boolean;
  message?: string;
  createdAt?: string;
}

export interface CreateTenantRequest {
  companyId: string;
  name: string;
  nit?: string;
  slug: string;
  address?: string;
  city?: string;
  department?: string;
  phone?: string;
  email?: string;
}

export interface TenantView {
  id?: string;
  name?: string;
  nit?: string;
  slug?: string;
  address?: string;
  city?: string;
  department?: string;
  phone?: string;
  email?: string;
  currency?: string;
  timezone?: string;
  locale?: string;
  status?: string;
  features?: Record<string, boolean>;
}

export interface CreateCompanyRequest {
  name: string;
  nit?: string;
}

export interface Company {
  id?: string;
  name?: string;
  nit?: string;
  status?: string;
  createdAt?: string;
}

export interface PaymentRequest {
  unitId: string;
  amount: number;
  paymentDate?: string;
  method: "CASH" | "TRANSFER" | "WOMPI" | "OTHER";
  reference?: string;
  payerName?: string;
  notes?: string;
}

export interface AllocationView {
  chargeId?: string;
  conceptType?: "ORDINARY" | "EXTRAORDINARY" | "INTEREST" | "FINE" | "OTHER";
  chargeDescription?: string;
  amount?: number;
  reversedAt?: string;
}

export interface Payment {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  amount?: number;
  unappliedAmount?: number;
  paymentDate?: string;
  method?: "CASH" | "TRANSFER" | "WOMPI" | "OTHER";
  source?: string;
  reference?: string;
  payerName?: string;
  notes?: string;
  status?: string;
  createdAt?: string;
  reversedAt?: string;
  reverseReason?: string;
}

export interface PaymentResult {
  payment?: Payment;
  allocations?: AllocationView[];
  replayed?: boolean;
}

export interface ReverseRequest {
  reason: string;
}

export interface ParkingRequest {
  code: string;
  kind: string;
  unitId?: string;
}

export interface ParkingSpace {
  id?: string;
  code?: string;
  kind?: string;
  unitId?: string;
  status?: string;
}

export interface ReceiveRequest {
  unitId: string;
  recipientName: string;
  carrier?: string;
  trackingNumber?: string;
  description?: string;
}

export interface Parcel {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  recipientName?: string;
  carrier?: string;
  trackingNumber?: string;
  description?: string;
  status?: string;
  receivedAt?: string;
  notifiedAt?: string;
  deliveredAt?: string;
  deliveredTo?: string;
  returnedAt?: string;
  note?: string;
}

export interface ReturnRequest {
  note?: string;
}

export interface DeliverRequest {
  deliveredTo: string;
}

export interface DecisionRequest {
  note?: string;
}

export interface InvitationRequest {
  unitId: string;
  visitorName: string;
  documentNumber?: string;
  phone?: string;
  plate?: string;
  peopleCount?: number;
  maxEntries?: number;
  validFrom?: string;
  validTo: string;
  notes?: string;
}

export interface InvitationCreated {
  invitation?: Invitation;
  qrToken?: string;
}

export interface CreateMyReservationRequest {
  commonAreaId: string;
  unitId: string;
  startTime: string;
  endTime: string;
  paymentMethod?: "NONE" | "CASH" | "WOMPI" | "BANK_TRANSFER";
  paymentReference?: string;
  notes?: string;
  acceptedTerms?: boolean;
}

export interface CancelMyReservationRequest {
  reason?: string;
}

export interface CreateMyPqrRequest {
  unitId?: string;
  category: "PETICION" | "QUEJA" | "RECLAMO" | "SUGERENCIA" | "CONVIVENCIA_RUIDO" | "CONVIVENCIA_MASCOTAS" | "CONVIVENCIA_GENERAL" | "OTRO";
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  title: string;
  description: string;
  isAnonymous?: boolean;
}

export interface ResidentMessageRequest {
  message: string;
}

export interface CreateIntentRequest {
  unitId: string;
  amount: number;
  notes?: string;
}

export interface IntentResponse {
  reference?: string;
  amountInCents?: number;
  currency?: string;
  signature?: string;
  publicKey?: string;
  checkoutUrl?: string;
  redirectUrl?: string;
  status?: string;
  netAmountInCents?: number;
  platformFeeInCents?: number;
  commissionInCents?: number;
}

export interface OnlinePaymentsSummary {
  approvedPayments?: number;
  pendingPayments?: number;
  chargedInCents?: number;
  creditedInCents?: number;
  platformFeesInCents?: number;
  wompiFeesInCents?: number;
  platformRevenueInCents?: number;
}

export interface OnlinePaymentQuote {
  netAmountInCents?: number;
  platformFeeInCents?: number;
  commissionInCents?: number;
  totalInCents?: number;
  currency?: string;
}

export interface VoteRequest {
  unitId: string;
  choice: string;
}

export interface AssemblyVote {
  id?: string;
  topicId?: string;
  unitId?: string;
  personId?: string;
  choice?: string;
  weight?: number;
  votedAt?: string;
}

export interface CreateWorkOrderRequest {
  assetId?: string;
  planId?: string;
  supplierId?: string;
  type?: "PREVENTIVE" | "CORRECTIVE" | "EMERGENCY" | "IMPROVEMENT";
  title: string;
  description: string;
  priority?: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  estimatedCost?: number;
  assignedTo?: string;
  scheduledStart?: string;
  scheduledEnd?: string;
}

export interface CreatePlanRequest {
  assetId?: string;
  title: string;
  description?: string;
  frequencyDays?: number;
  nextDueDate?: string;
  assignedSupplierId?: string;
}

export interface MaintenancePlan {
  id?: string;
  assetId?: string;
  title?: string;
  description?: string;
  frequencyDays?: number;
  lastPerformedAt?: string;
  nextDueDate?: string;
  assignedSupplierId?: string;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface IncidentRequest {
  category: "DANO" | "SEGURIDAD" | "RUIDO" | "VEHICULO" | "VISITANTE" | "INFRAESTRUCTURA" | "EMERGENCIA" | "OTRO";
  description: string;
  location?: string;
  occurredAt?: string;
}

export interface Document {
  id?: string;
  title?: string;
  description?: string;
  category?: "ACTA_ASAMBLEA" | "ESTADO_FINANCIERO" | "REGLAMENTO_PROPIEDAD_HORIZONTAL" | "PRESUPUESTO" | "CONTRATO" | "CIRCULAR" | "OTRO";
  storageKey?: string;
  fileName?: string;
  fileSize?: number;
  contentType?: string;
  minimumRole?: string;
  uploadedBy?: string;
  isActive?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateCommunicationRequest {
  title: string;
  summary?: string;
  content: string;
  priority?: "NORMAL" | "IMPORTANT" | "URGENT";
  targetAudience?: "ALL" | "OWNERS" | "TENANTS" | "RESIDENTS" | "COUNCIL";
  targetTower?: string;
  isPinned?: boolean;
  expiresAt?: string;
  publishNow?: boolean;
}

export interface Communication {
  id?: string;
  title?: string;
  summary?: string;
  content?: string;
  priority?: "NORMAL" | "IMPORTANT" | "URGENT";
  targetAudience?: "ALL" | "OWNERS" | "TENANTS" | "RESIDENTS" | "COUNCIL";
  targetTower?: string;
  status?: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  isPinned?: boolean;
  createdBy?: string;
  publishedAt?: string;
  expiresAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface DetectRequest {
  source?: string;
  plate?: string;
}

export interface PlateDetectionResult {
  plate?: string;
  confidence?: number;
  capturedAt?: string;
  cameraSource?: string;
  registered?: boolean;
  vehicleDetails?: string;
  message?: string;
}

export interface CreateBudgetRequest {
  year?: number;
  title: string;
}

export interface AnnualBudget {
  id?: string;
  year?: number;
  title?: string;
  status?: "DRAFT" | "APPROVED" | "CLOSED";
  totalIncomeBudgeted?: number;
  totalExpenseBudgeted?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateItemRequest {
  code: string;
  name: string;
  type: "INCOME" | "EXPENSE";
  budgetedAmount?: number;
}

export interface BudgetItem {
  id?: string;
  budgetId?: string;
  code?: string;
  name?: string;
  type?: "INCOME" | "EXPENSE";
  budgetedAmount?: number;
  executedAmount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface ExecuteBudgetRequest {
  workOrderId?: string;
  supplierId?: string;
  amount: number;
  executionDate?: string;
  invoiceNumber?: string;
  description: string;
}

export interface BudgetExecution {
  id?: string;
  budgetItemId?: string;
  workOrderId?: string;
  supplierId?: string;
  amount?: number;
  executionDate?: string;
  invoiceNumber?: string;
  description?: string;
  registeredBy?: string;
  createdAt?: string;
}

export interface OrdinaryRunRequest {
  period: string;
  dueDate: string;
  totalBudget?: number;
  fixedAmount?: number;
}

export interface RunSummary {
  period?: string;
  created?: number;
  skippedExisting?: number;
  skippedNoCoefficient?: number;
  totalAmount?: number;
}

export interface InterestRunRequest {
  asOf?: string;
}

export interface InterestRunSummary {
  asOf?: string;
  unitsCharged?: number;
  totalInterest?: number;
}

export interface ChargeRequest {
  unitId: string;
  type: "ORDINARY" | "EXTRAORDINARY" | "INTEREST" | "FINE" | "OTHER";
  description: string;
  period?: string;
  issueDate?: string;
  dueDate: string;
  amount: number;
}

export interface Charge {
  id?: string;
  unitId?: string;
  unitIdentifier?: string;
  type?: "ORDINARY" | "EXTRAORDINARY" | "INTEREST" | "FINE" | "OTHER";
  description?: string;
  period?: string;
  issueDate?: string;
  dueDate?: string;
  amount?: number;
  adjustedAmount?: number;
  paidAmount?: number;
  outstanding?: number;
  state?: string;
  interestAccruedUntil?: string;
  voidedAt?: string;
  voidReason?: string;
  createdAt?: string;
}

export interface ReasonRequest {
  reason: string;
}

export interface AdjustmentRequest {
  kind: "CREDIT" | "DEBIT";
  amount: number;
  reason: string;
}

export interface BankRow {
  id?: string;
  importId?: string;
  rowNumber?: number;
  txnDate?: string;
  amount?: number;
  description?: string;
  reference?: string;
  counterparty?: string;
  bankTxnId?: string;
  status?: "AUTO_MATCHED" | "MANUAL_REVIEW" | "MATCHED" | "REJECTED" | "DUPLICATE" | "AMBIGUOUS" | "IGNORED";
  matchScore?: number;
  unitId?: string;
  unitIdentifier?: string;
  paymentId?: string;
  applyCount?: number;
  decidedBy?: string;
  decidedAt?: string;
  decisionReason?: string;
}

export interface Candidate {
  unitId?: string;
  unitIdentifier?: string;
  score?: number;
  signals?: string;
}

export interface Decision {
  id?: string;
  action?: string;
  fromStatus?: string;
  toStatus?: string;
  unitId?: string;
  paymentId?: string;
  reason?: string;
  decidedBy?: string;
  decidedAt?: string;
}

export interface RowDetail {
  row?: BankRow;
  candidates?: Candidate[];
  decisions?: Decision[];
}

export interface MatchRequest {
  unitId: string;
  reason: string;
}

export interface BankImport {
  id?: string;
  bankName?: string;
  accountLabel?: string;
  profileId?: string;
  filename?: string;
  format?: string;
  status?: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
  totalRows?: number;
  autoMatched?: number;
  manualReview?: number;
  ambiguous?: number;
  duplicates?: number;
  ignored?: number;
  errorMessage?: string;
  uploadedBy?: string;
  createdAt?: string;
  startedAt?: string;
  completedAt?: string;
}

export interface SelectTenantRequest {
  tenantId: string;
}

export interface TenantTokenResult {
  accessToken?: string;
  tokenType?: string;
  expiresIn?: number;
  tenantId?: string;
  tenantName?: string;
  role?: string;
  permissions?: string[];
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface SessionResult {
  accessToken?: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  user?: UserView;
  tenants?: TenantSummary[];
  activeTenantId?: string;
  activeRole?: string;
  permissions?: string[];
}

export interface TenantSummary {
  id?: string;
  name?: string;
  slug?: string;
  role?: string;
}

export interface TenantOverviewMetrics {
  units?: number;
  people?: number;
  openPqrs?: number;
  openIncidents?: number;
  upcomingReservations?: number;
  pendingPackages?: number;
  overdueCharges?: number;
  overdueAmount?: number;
}

export interface TenantOverview {
  id?: string;
  name?: string;
  slug?: string;
  role?: string;
  canManage?: boolean;
  metrics?: TenantOverviewMetrics | null;
}

export interface WompiIntegrationView {
  configured?: boolean;
  inherited?: boolean;
  publicKey?: string;
  apiUrl?: string;
}

export interface AiIntegrationView {
  configured?: boolean;
  inherited?: boolean;
  provider?: string;
  model?: string;
}

export interface TenantIntegrations {
  wompi?: WompiIntegrationView;
  ai?: AiIntegrationView;
}

export interface UserView {
  id?: string;
  email?: string;
  fullName?: string;
  platformRole?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface CreateAssemblyRequest {
  title: string;
  type: "ORDINARIA" | "EXTRAORDINARIA";
  scheduledAt: string;
  location?: string;
  isVirtual?: boolean;
  meetingLink?: string;
  requiredQuorumPercentage?: number;
}

export interface Assembly {
  id?: string;
  title?: string;
  type?: "ORDINARIA" | "EXTRAORDINARIA";
  scheduledAt?: string;
  status?: "SCHEDULED" | "IN_PROGRESS" | "ADJOURNED" | "CLOSED";
  location?: string;
  isVirtual?: boolean;
  meetingLink?: string;
  requiredQuorumPercentage?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateTopicRequest {
  orderNum?: number;
  title: string;
  description?: string;
  votingType?: "NOMINAL" | "COEFFICIENT";
  requiresQualifiedMajority?: boolean;
  options?: string[];
}

export interface AssemblyTopic {
  id?: string;
  assemblyId?: string;
  orderNum?: number;
  title?: string;
  description?: string;
  votingType?: "NOMINAL" | "COEFFICIENT";
  status?: "PENDING" | "VOTING_OPEN" | "VOTING_CLOSED";
  requiresQualifiedMajority?: boolean;
  options?: string[];
  createdAt?: string;
}

export interface CheckInRequest {
  unitId: string;
  personId?: string;
  isProxy?: boolean;
  proxyName?: string;
}

export interface QuorumSummary {
  registeredUnits?: number;
  totalUnits?: number;
  currentQuorumPercentage?: number;
  requiredQuorumPercentage?: number;
  hasQuorum?: boolean;
}

export interface AccessRequest {
  plate: string;
  method?: string;
  cameraId?: string;
}

export interface AccessEvent {
  id?: string;
  vehicleId?: string;
  unitId?: string;
  plate?: string;
  eventType?: string;
  occurredAt?: string;
  guardUserId?: string;
  cameraId?: string;
  source?: string;
  method?: string;
  entryEventId?: string;
}

export interface ChangeRoleRequest {
  role: string;
}

export interface StatusRequest {
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED" | "REJECTED";
  resolutionNotes?: string;
}

export interface PageResultVisit {
  content?: Visit[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultInvitation {
  content?: Invitation[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultPet {
  content?: Pet[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultVehicle {
  content?: Vehicle[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultMemberView {
  content?: MemberView[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface AccessHistoryEntry {
  event?: "GRANTED" | "ROLE_CHANGED" | "SUSPENDED" | "ACTIVATED" | "REVOKED" | "ENTERED" | "LEFT";
  roleCode?: string;
  actorUserId?: string;
  occurredAt?: string;
  ip?: string;
}

export interface PageResultSupplier {
  content?: Supplier[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface SecuritySummary {
  vehiclesInside?: number;
  visitorsInside?: number;
  pendingVisitRequests?: number;
  openAlerts?: number;
  packagesPending?: number;
  openIncidents?: number;
  vehicleEntriesToday?: number;
}

export interface PageResultPerson {
  content?: Person[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultReservation {
  content?: Reservation[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PortfolioReport {
  items?: PortfolioReportItem[];
  totalDueSum?: number;
  totalOverdueSum?: number;
  totalCreditSum?: number;
  totalUnitsWithDebt?: number;
}

export interface PortfolioReportItem {
  unitIdentifier?: string;
  tower?: string;
  totalDue?: number;
  overdueAmount?: number;
  creditBalance?: number;
  overdueChargesCount?: number;
}

export interface MaintenanceReport {
  totalAssets?: number;
  activePlans?: number;
  totalWorkOrders?: number;
  completedWorkOrders?: number;
  inProgressWorkOrders?: number;
  pendingWorkOrders?: number;
  totalEstimatedCost?: number;
  totalActualCost?: number;
}

export interface BudgetReport {
  year?: number;
  title?: string;
  incomeBudgeted?: number;
  incomeExecuted?: number;
  expenseBudgeted?: number;
  expenseExecuted?: number;
  netBudgeted?: number;
  netExecuted?: number;
}

export interface PublicConfig {
  appName?: string;
  company?: string;
}

export interface CertificateVerification {
  verificationCode?: string;
  tenantId?: string;
  tenantName?: string;
  certificateType?: "PAZ_Y_SALVO" | "ESTADO_CUENTA" | "CERTIFICADO_PROPIEDAD";
  unitIdentifier?: string;
  recipientName?: string;
  issuedAt?: string;
  status?: string;
  contentHash?: string;
}

export interface PageResultPropertyUnit {
  content?: PropertyUnit[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultPqr {
  content?: Pqr[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PqrDetail {
  pqr?: Pqr;
  messages?: PqrMessage[];
}

export interface PqrSlaMetrics {
  totalOpen?: number;
  onTimeCount?: number;
  overdueCount?: number;
  avgResolutionHours?: number;
}

export interface PageResultTenantView {
  content?: TenantView[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface AuditLogView {
  id?: string;
  occurredAt?: string;
  tenantId?: string;
  actorUserId?: string;
  actorEmail?: string;
  action?: string;
  entity?: string;
  entityId?: string;
  result?: string;
  ip?: string;
  traceId?: string;
  details?: string;
}

export interface PageResultAuditLogView {
  content?: AuditLogView[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultPayment {
  content?: Payment[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultParcel {
  content?: Parcel[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface NotificationLog {
  id?: string;
  recipientEmail?: string;
  recipientPhone?: string;
  userId?: string;
  channel?: "EMAIL" | "PUSH" | "WHATSAPP";
  type?: "PACKAGE_ARRIVED" | "VISITOR_ARRIVED" | "PORTFOLIO_DUE" | "PORTFOLIO_OVERDUE" | "PAYMENT_RECEIVED" | "RESERVATION_UPDATE" | "PQR_UPDATE" | "COMMUNICATION";
  subject?: string;
  body?: string;
  status?: "SENT" | "FAILED" | "SKIPPED";
  errorDetail?: string;
  createdAt?: string;
}

export interface PageResultNotificationLog {
  content?: NotificationLog[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface MyUnit {
  unitId?: string;
  identifier?: string;
  tower?: string;
  unitType?: string;
  relations?: string[];
}

export interface LedgerEntry {
  id?: string;
  unitId?: string;
  entryType?: string;
  amount?: number;
  entryDate?: string;
  chargeId?: string;
  paymentId?: string;
  conceptType?: string;
  description?: string;
  reason?: string;
  createdBy?: string;
  createdAt?: string;
}

export interface Statement {
  unitId?: string;
  unitIdentifier?: string;
  from?: string;
  to?: string;
  openingBalance?: number;
  chargesTotal?: number;
  interestTotal?: number;
  paymentsTotal?: number;
  adjustmentsTotal?: number;
  closingBalance?: number;
  overdueAmount?: number;
  nextDueDate?: string;
  creditBalance?: number;
  lines?: LedgerEntry[];
  openCharges?: Charge[];
}

export interface OnlinePaymentIntent {
  id?: string;
  reference?: string;
  tenantId?: string;
  unitId?: string;
  userId?: string;
  amountInCents?: number;
  currency?: string;
  signature?: string;
  status?: "PENDING" | "APPROVED" | "DECLINED" | "VOIDED" | "ERROR";
  wompiTxId?: string;
  paymentMethod?: string;
  feeInCents?: number;
  platformFeeInCents?: number;
  netAmountInCents?: number;
  errorMessage?: string;
  paymentId?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResultResidentCommunicationItem {
  content?: ResidentCommunicationItem[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface ResidentCommunicationItem {
  communication?: Communication;
  isRead?: boolean;
}

export interface IssuedCertificate {
  id?: string;
  verificationCode?: string;
  certificateType?: "PAZ_Y_SALVO" | "ESTADO_CUENTA" | "CERTIFICADO_PROPIEDAD";
  unitId?: string;
  personId?: string;
  issuedBy?: string;
  issuedAt?: string;
  expiresAt?: string;
  recipientName?: string;
  recipientDocType?: string;
  recipientDocNum?: string;
  unitIdentifier?: string;
  status?: string;
  snapshotData?: string;
  contentHash?: string;
}

export interface PageResultWorkOrder {
  content?: WorkOrder[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultIncident {
  content?: Incident[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultCommunication {
  content?: Communication[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface TimeSlot {
  startTime?: string;
  endTime?: string;
  available?: boolean;
}

export interface BudgetSummary {
  budget?: AnnualBudget;
  items?: BudgetItem[];
  totalIncomeBudgeted?: number;
  totalIncomeExecuted?: number;
  totalExpenseBudgeted?: number;
  totalExpenseExecuted?: number;
  netBudgeted?: number;
  netExecuted?: number;
}

export interface PageResultLedgerEntry {
  content?: LedgerEntry[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultPortfolioRow {
  content?: PortfolioRow[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PortfolioRow {
  unitId?: string;
  unitIdentifier?: string;
  outstanding?: number;
  overdue?: number;
  creditBalance?: number;
  oldestDueDate?: string;
}

export interface PortfolioView {
  totalOutstanding?: number;
  totalOverdue?: number;
  totalCredit?: number;
  units?: PageResultPortfolioRow;
}

export interface PageResultCharge {
  content?: Charge[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultBankRow {
  content?: BankRow[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultBankImport {
  content?: BankImport[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface MeResult {
  user?: UserView;
  tenants?: TenantSummary[];
  activeTenantId?: string;
  activeRole?: string;
  permissions?: string[];
}

export interface AssemblyAttendance {
  assemblyId?: string;
  unitId?: string;
  personId?: string;
  isProxy?: boolean;
  proxyName?: string;
  checkedInAt?: string;
}

export interface TopicResults {
  topicId?: string;
  title?: string;
  votingType?: "NOMINAL" | "COEFFICIENT";
  status?: "PENDING" | "VOTING_OPEN" | "VOTING_CLOSED";
  totalVotes?: number;
  totalWeight?: number;
  voteTallies?: Record<string, number>;
  percentageTallies?: Record<string, number>;
  winningOption?: string;
}

export interface VehicleLookup {
  vehicleId?: string;
  plate?: string;
  typeCode?: string;
  brand?: string;
  color?: string;
  unitIdentifier?: string;
  status?: string;
  presence?: string;
}

export interface PageResultAccessEvent {
  content?: AccessEvent[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultSecurityAlert {
  content?: SecurityAlert[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface SecurityAlert {
  id?: string;
  alertType?: string;
  vehicleId?: string;
  plate?: string;
  unitId?: string;
  message?: string;
  createdAt?: string;
  resolvedAt?: string;
}


export interface UpdateTenantRequest {
  name: string;
  nit?: string;
  address?: string;
  city?: string;
  department?: string;
  phone?: string;
  email?: string;
}

export interface PayrollSettings {
  currency?: string;
  periodicity?: "MONTHLY" | "BIWEEKLY";
  baseDays?: number;
  healthEmployeeRate?: number;
  pensionEmployeeRate?: number;
  healthEmployerRate?: number;
  pensionEmployerRate?: number;
  arlRate?: number;
  compensationRate?: number;
  senaRate?: number;
  icbfRate?: number;
  severanceRate?: number;
  vacationRate?: number;
  severanceInterestRate?: number;
  transportAllowance?: number;
  minimumWage?: number;
  updatedAt?: string;
}

export interface PayrollEmployee {
  id?: string;
  documentType?: string;
  documentNumber?: string;
  fullName?: string;
  position?: string;
  baseSalary?: number;
  startDate?: string;
  endDate?: string;
  status?: "ACTIVE" | "INACTIVE";
  bankName?: string;
  bankAccount?: string;
  costCenter?: string;
  fixedDeductions?: number;
  notes?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PayrollEmployeeRequest {
  documentType: string;
  documentNumber: string;
  fullName: string;
  position: string;
  baseSalary: number;
  startDate: string;
  endDate?: string;
  status: "ACTIVE" | "INACTIVE";
  bankName?: string;
  bankAccount?: string;
  costCenter?: string;
  fixedDeductions?: number;
  notes?: string;
}

export interface PayrollItem {
  id?: string;
  employeeId?: string;
  employeeName?: string;
  documentNumber?: string;
  position?: string;
  baseSalary?: number;
  workedDays?: number;
  grossPay?: number;
  healthDeduction?: number;
  pensionDeduction?: number;
  otherDeductions?: number;
  netPay?: number;
  healthEmployer?: number;
  pensionEmployer?: number;
  arl?: number;
  compensation?: number;
  sena?: number;
  icbf?: number;
  severance?: number;
  vacation?: number;
  severanceInterest?: number;
  employerCost?: number;
}

export interface PayrollRun {
  id?: string;
  runNumber?: string;
  period?: string;
  status?: "DRAFT" | "CALCULATED" | "PAID" | "VOID";
  totalGross?: number;
  totalDeductions?: number;
  totalNet?: number;
  totalEmployerCost?: number;
  notes?: string;
  createdBy?: string;
  createdAt?: string;
  calculatedAt?: string;
  paidAt?: string;
  voidedAt?: string;
  voidReason?: string;
  items?: PayrollItem[];
}

export interface PageResultPayrollEmployee {
  content?: PayrollEmployee[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}

export interface PageResultPayrollRun {
  content?: PayrollRun[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
}
