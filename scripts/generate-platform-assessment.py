#!/usr/bin/env python3
"""Generate the platform architecture PDF and editable cost-model workbook."""

from __future__ import annotations

from pathlib import Path

from openpyxl import Workbook
from openpyxl.chart import BarChart, Reference
from openpyxl.comments import Comment
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.graphics.shapes import Drawing, Line, Rect, String


ROOT = Path(__file__).resolve().parents[1]
DOCS = ROOT / "docs"
XLSX_PATH = DOCS / "factori-cost-model.xlsx"
PDF_PATH = DOCS / "factori-platform-assessment.pdf"

APP_SCENARIOS = (100, 1_000, 10_000)
COMMON = {
    "builds": 1,
    "versions": 3,
    "apk_mb": 60,
    "installations": 500,
    "messages": 4,
    "fcm_cost": 0.0,
    "storage_rate": 2.0,
    "key_rate": 30.0,
    "fte_rate": 300_000.0,
}
APPROACHES = (
    {
        "name": "Self-hosted",
        "infra": 75_000,
        "database": 30_000,
        "monitoring": 10_000,
        "build_hours": 0.15,
        "build_rate": 150,
        "platform_fee": 0,
        "maintenance_fte": 1.5,
        "setup_fte_months": 18,
        "other": 5_000,
    },
    {
        "name": "Managed platform",
        "infra": 25_000,
        "database": 15_000,
        "monitoring": 15_000,
        "build_hours": 0,
        "build_rate": 0,
        "platform_fee": 300,
        "maintenance_fte": 0.75,
        "setup_fte_months": 4,
        "other": 10_000,
    },
    {
        "name": "Hybrid",
        "infra": 45_000,
        "database": 25_000,
        "monitoring": 8_000,
        "build_hours": 0.12,
        "build_rate": 150,
        "platform_fee": 0,
        "maintenance_fte": 1,
        "setup_fte_months": 10,
        "other": 7_000,
    },
)


def calculate_cost(apps: int, approach: dict[str, float | str]) -> tuple[float, float]:
    storage_gb = (
        apps * COMMON["builds"] * COMMON["versions"] * COMMON["apk_mb"] / 1024
    )
    monthly = (
        storage_gb * COMMON["storage_rate"]
        + apps * COMMON["builds"] * approach["build_hours"] * approach["build_rate"]
        + apps * COMMON["key_rate"]
        + apps * COMMON["installations"] * COMMON["messages"] * COMMON["fcm_cost"]
        + apps * approach["platform_fee"]
        + approach["maintenance_fte"] * COMMON["fte_rate"]
        + approach["infra"]
        + approach["database"]
        + approach["monitoring"]
        + approach["other"]
    )
    setup = approach["setup_fte_months"] * COMMON["fte_rate"]
    return monthly, setup


def style_workbook(workbook: Workbook) -> None:
    navy = "17365D"
    blue = "D9EAF7"
    yellow = "FFF2CC"
    green = "E2F0D9"
    white = "FFFFFF"
    thin_gray = Side(style="thin", color="D9E1F2")
    for sheet in workbook.worksheets:
        sheet.sheet_view.showGridLines = False
        sheet.freeze_panes = "A3"
        sheet.row_dimensions[1].height = 30
        sheet["A1"].font = Font(bold=True, size=16, color=white)
        sheet["A1"].fill = PatternFill("solid", fgColor=navy)
        sheet["A1"].alignment = Alignment(vertical="center")
        for cell in sheet[2]:
            cell.font = Font(bold=True, color=white)
            cell.fill = PatternFill("solid", fgColor=navy)
            cell.alignment = Alignment(wrap_text=True, vertical="center")
        for row in sheet.iter_rows(min_row=2):
            for cell in row:
                cell.border = Border(bottom=thin_gray)
                if cell.data_type == "f":
                    cell.fill = PatternFill("solid", fgColor=green)
        for column in range(1, sheet.max_column + 1):
            letter = get_column_letter(column)
            width = max(
                12,
                min(
                    36,
                    max(
                        len(str(sheet.cell(row, column).value or ""))
                        for row in range(1, sheet.max_row + 1)
                    )
                    + 2,
                ),
            )
            sheet.column_dimensions[letter].width = width
    inputs = workbook["Inputs"]
    for row in range(3, 12):
        inputs.cell(row, 2).fill = PatternFill("solid", fgColor=yellow)
    for row in range(14, 17):
        for column in range(2, 11):
            inputs.cell(row, column).fill = PatternFill("solid", fgColor=yellow)
    inputs.freeze_panes = "B4"


def build_workbook() -> None:
    workbook = Workbook()
    overview = workbook.active
    overview.title = "Overview"
    overview.append(["Factori Mobile Platform - Cost Model"])
    overview.append(
        [
            "Scale",
            "Approach",
            "Recurring / month (INR)",
            "One-time setup (INR)",
            "Year 1 avg / month (INR)",
            "Recurring / app / month (INR)",
            "Year 1 / app (INR)",
        ]
    )

    inputs = workbook.create_sheet("Inputs")
    inputs.append(["Editable Planning Inputs - INR"])
    inputs.append(["Input", "Value", "Unit / notes"])
    common_rows = [
        ("Builds per app per month", COMMON["builds"], "builds/app/month"),
        ("APK versions retained", COMMON["versions"], "versions/app"),
        ("Average APK artifact size", COMMON["apk_mb"], "MB"),
        ("Active installations per app", COMMON["installations"], "devices/app"),
        ("FCM messages per installation", COMMON["messages"], "messages/device/month"),
        ("Direct FCM price", COMMON["fcm_cost"], "INR/message; FCM currently no-cost"),
        ("Artifact storage rate", COMMON["storage_rate"], "INR/GB-month; planning assumption"),
        ("Key management per app", COMMON["key_rate"], "INR/app/month; planning assumption"),
        ("Engineering cost", COMMON["fte_rate"], "INR/FTE-month; planning assumption"),
    ]
    for label, value, note in common_rows:
        inputs.append([label, value, note])
    inputs.append([])
    inputs.append(
        [
            "Approach",
            "Fixed infrastructure/mo",
            "Database/mo",
            "Monitoring/mo",
            "Build hours/build",
            "Build INR/hour",
            "Platform fee/app/mo",
            "Maintenance FTE",
            "Setup FTE-months",
            "Other fixed/mo",
        ]
    )
    for approach in APPROACHES:
        inputs.append(
            [
                approach["name"],
                approach["infra"],
                approach["database"],
                approach["monitoring"],
                approach["build_hours"],
                approach["build_rate"],
                approach["platform_fee"],
                approach["maintenance_fte"],
                approach["setup_fte_months"],
                approach["other"],
            ]
        )
    inputs.append([])
    inputs.append(["Scenario app counts", "Apps", "Notes"])
    for apps in APP_SCENARIOS:
        inputs.append([f"{apps:,} retailers/apps", apps, "Illustrative scale; edit count"])
    inputs["B8"].comment = Comment(
        "FCM itself is currently documented as no-cost. Associated compute, egress, storage, and adjacent services may cost extra.",
        "Factori",
    )
    inputs["B9"].comment = Comment(
        "Editable planning assumption, not a quoted cloud price.",
        "Factori",
    )
    inputs["B10"].comment = Comment(
        "Editable planning assumption, not a quoted KMS price.",
        "Factori",
    )
    inputs["B11"].comment = Comment(
        "Editable labor planning assumption; replace with Factori loaded cost.",
        "Factori",
    )

    model = workbook.create_sheet("Model")
    model.append(["Monthly Recurring and Year-One Cost Calculation"])
    model.append(
        [
            "Apps",
            "Approach",
            "Artifact GB",
            "Storage",
            "Builds/mo",
            "Build compute",
            "Key mgmt",
            "FCM direct",
            "Platform fee",
            "Staffing",
            "Infrastructure",
            "Database",
            "Monitoring",
            "Other",
            "Recurring total",
            "Recurring/app",
            "Setup one-time",
            "Year 1 total",
            "Year 1 avg/mo",
            "Year 1/app",
        ]
    )
    row_num = 3
    for scenario_index, apps in enumerate(APP_SCENARIOS):
        scenario_row = 19 + scenario_index
        for approach_index, approach in enumerate(APPROACHES):
            input_row = 14 + approach_index
            model.cell(row_num, 1, f"=Inputs!$B${scenario_row}")
            model.cell(row_num, 2, approach["name"])
            model.cell(
                row_num,
                3,
                f"=A{row_num}*Inputs!$B$3*Inputs!$B$4*Inputs!$B$5/1024",
            )
            model.cell(row_num, 4, f"=C{row_num}*Inputs!$B$9")
            model.cell(row_num, 5, f"=A{row_num}*Inputs!$B$3")
            model.cell(
                row_num,
                6,
                f"=E{row_num}*Inputs!$E${input_row}*Inputs!$F${input_row}",
            )
            model.cell(row_num, 7, f"=A{row_num}*Inputs!$B$10")
            model.cell(
                row_num,
                8,
                f"=A{row_num}*Inputs!$B$6*Inputs!$B$7*Inputs!$B$8",
            )
            model.cell(row_num, 9, f"=A{row_num}*Inputs!$G${input_row}")
            model.cell(
                row_num, 10, f"=Inputs!$H${input_row}*Inputs!$B$11"
            )
            for column, input_column in ((11, "B"), (12, "C"), (13, "D"), (14, "J")):
                model.cell(
                    row_num,
                    column,
                    f"=Inputs!${input_column}${input_row}",
                )
            model.cell(row_num, 15, f"=SUM(D{row_num}:N{row_num})")
            model.cell(row_num, 16, f"=O{row_num}/A{row_num}")
            model.cell(
                row_num, 17, f"=Inputs!$I${input_row}*Inputs!$B$11"
            )
            model.cell(row_num, 18, f"=O{row_num}*12+Q{row_num}")
            model.cell(row_num, 19, f"=R{row_num}/12")
            model.cell(row_num, 20, f"=R{row_num}/A{row_num}")
            overview_row = row_num
            overview.cell(overview_row, 1, f"=Model!A{row_num}")
            overview.cell(overview_row, 2, f"=Model!B{row_num}")
            overview.cell(overview_row, 3, f"=Model!O{row_num}")
            overview.cell(overview_row, 4, f"=Model!Q{row_num}")
            overview.cell(overview_row, 5, f"=Model!S{row_num}")
            overview.cell(overview_row, 6, f"=Model!P{row_num}")
            overview.cell(overview_row, 7, f"=Model!T{row_num}")
            row_num += 1

    sources = workbook.create_sheet("Sources & Notes")
    sources.append(["Sources, Definitions, and Limitations"])
    sources.append(["Item", "Details"])
    notes = [
        (
            "Currency",
            "INR. All values are editable planning assumptions, not vendor quotes.",
        ),
        (
            "Build and storage",
            "Artifact storage estimates assume each APK build has the average size, one build/app/month, and three retained versions. Logs, source archives, backups, replicas, egress, and taxes are excluded.",
        ),
        (
            "FCM",
            "Direct Firebase Cloud Messaging usage is documented as no-cost. The workbook models direct message price as zero, but supporting compute, data transfer, Firebase adjacent services, and operations may cost extra.",
        ),
        (
            "Staffing",
            "FTE rates, setup effort, and monthly maintenance are illustrative planning values. Replace them with loaded Factori team costs and delivery estimates.",
        ),
        (
            "Approaches",
            "Self-hosted, managed-platform, and hybrid values are placeholders to compare scaling drivers, not market-validated rates.",
        ),
        (
            "Eligibility",
            "Target rule in the prototype is >=500 completed orders OR >=INR 250,000 qualifying sales since store launch. The authoritative source and calculation definition remain open.",
        ),
        (
            "FCM pricing source",
            "https://firebase.google.com/docs/cloud-messaging",
        ),
        (
            "Google Play target API source",
            "https://support.google.com/googleplay/android-developer/answer/11926878",
        ),
        (
            "Checked date",
            "2026-11-18; policy pages and vendor prices can change.",
        ),
    ]
    for note in notes:
        sources.append(list(note))
    sources.column_dimensions["A"].width = 28
    sources.column_dimensions["B"].width = 110
    for row in sources.iter_rows(min_row=3, min_col=2, max_col=2):
        row[0].alignment = Alignment(wrap_text=True, vertical="top")
    for row in range(3, sources.max_row + 1):
        sources.row_dimensions[row].height = 40

    for row in range(3, 12):
        overview.cell(row, 1).number_format = "#,##0"
        for column in range(3, 8):
            overview.cell(row, column).number_format = '"INR " #,##0.00'
    for row in range(3, 12):
        model.cell(row, 1).number_format = "#,##0"
        model.cell(row, 3).number_format = "#,##0.00"
        for column in range(4, 21):
            model.cell(row, column).number_format = '"INR " #,##0.00'
    overview.auto_filter.ref = f"A2:G{overview.max_row}"
    model.auto_filter.ref = f"A2:T{model.max_row}"
    chart = BarChart()
    chart.title = "Illustrative recurring monthly cost"
    chart.y_axis.title = "INR"
    chart.x_axis.title = "Apps"
    chart.style = 10
    chart.height = 7
    chart.width = 15
    data = Reference(model, min_col=15, min_row=2, max_row=11)
    categories = Reference(model, min_col=1, min_row=3, max_row=11)
    chart.add_data(data, titles_from_data=True)
    chart.set_categories(categories)
    overview.add_chart(chart, "I3")
    workbook.calculation.fullCalcOnLoad = True
    workbook.calculation.forceFullCalc = True
    workbook.calculation.calcMode = "auto"
    style_workbook(workbook)
    workbook.save(XLSX_PATH)


def report_styles():
    styles = getSampleStyleSheet()
    styles.add(
        ParagraphStyle(
            "CoverTitle",
            parent=styles["Title"],
            fontName="Helvetica-Bold",
            fontSize=21,
            leading=25,
            textColor=colors.HexColor("#17365D"),
            alignment=TA_CENTER,
            spaceAfter=12,
        )
    )
    styles.add(
        ParagraphStyle(
            "SectionTitle",
            parent=styles["Heading1"],
            fontName="Helvetica-Bold",
            fontSize=16,
            leading=19,
            textColor=colors.HexColor("#17365D"),
            spaceAfter=8,
        )
    )
    styles.add(
        ParagraphStyle(
            "SubTitle",
            parent=styles["Heading2"],
            fontSize=11,
            leading=14,
            textColor=colors.HexColor("#244A73"),
            spaceBefore=5,
            spaceAfter=4,
        )
    )
    styles.add(
        ParagraphStyle(
            "BodySmall",
            parent=styles["BodyText"],
            fontSize=8.7,
            leading=11.2,
            spaceAfter=5,
        )
    )
    styles.add(
        ParagraphStyle(
            "BulletSmall",
            parent=styles["BodySmall"],
            leftIndent=12,
            firstLineIndent=-8,
            bulletIndent=0,
            spaceAfter=3,
        )
    )
    return styles


def paragraph(text: str, style):
    return Paragraph(text, style)


def bullets(items: list[str], style) -> list[Paragraph]:
    return [paragraph(f"&bull; {item}", style) for item in items]


def make_table(rows, widths, font_size=8):
    cell_style = ParagraphStyle(
        "TableCell",
        fontName="Helvetica",
        fontSize=font_size,
        leading=font_size + 2,
        textColor=colors.HexColor("#222222"),
    )
    header_style = ParagraphStyle(
        "TableHeader",
        parent=cell_style,
        fontName="Helvetica-Bold",
        textColor=colors.white,
    )
    wrapped_rows = [
        [
            Paragraph(str(value), header_style if row_index == 0 else cell_style)
            for value in row
        ]
        for row_index, row in enumerate(rows)
    ]
    table = Table(wrapped_rows, colWidths=widths, repeatRows=1, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#17365D")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
                ("FONTSIZE", (0, 0), (-1, -1), font_size),
                ("LEADING", (0, 0), (-1, -1), font_size + 2),
                ("GRID", (0, 0), (-1, -1), 0.35, colors.HexColor("#CAD4E0")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F3F6FA")]),
                ("LEFTPADDING", (0, 0), (-1, -1), 5),
                ("RIGHTPADDING", (0, 0), (-1, -1), 5),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    return table


def architecture_diagram() -> Drawing:
    drawing = Drawing(493, 205)
    boxes = [
        (10, 130, 105, 48, "Retailer / operator", "dashboard not implemented"),
        (145, 130, 110, 48, "Spring Boot API", "JWT + tenant scope"),
        (290, 145, 95, 40, "PostgreSQL", "forced tenant RLS"),
        (290, 88, 95, 40, "Build queue", "in-process / bounded"),
        (410, 88, 82, 40, "Docker worker", "network disabled"),
        (410, 25, 82, 40, "APK + logs", "private local storage"),
        (10, 25, 105, 48, "Android app", "WebView storefront"),
        (145, 25, 110, 48, "Retailer site", "HTTPS / payment handoff"),
    ]
    for x, y, width, height, title, caption in boxes:
        drawing.add(
            Rect(
                x,
                y,
                width,
                height,
                rx=4,
                ry=4,
                fillColor=colors.HexColor("#EAF2F8"),
                strokeColor=colors.HexColor("#4472C4"),
            )
        )
        drawing.add(
            String(
                x + 5,
                y + height - 16,
                title,
                fontName="Helvetica-Bold",
                fontSize=8,
                fillColor=colors.HexColor("#17365D"),
            )
        )
        drawing.add(
            String(
                x + 5,
                y + 8,
                caption,
                fontName="Helvetica",
                fontSize=6.5,
                fillColor=colors.HexColor("#333333"),
            )
        )
    for start_x, start_y, end_x, end_y in (
        (115, 154, 145, 154),
        (255, 158, 290, 165),
        (255, 145, 290, 108),
        (385, 108, 410, 108),
        (451, 88, 451, 65),
        (115, 49, 145, 49),
    ):
        drawing.add(
            Line(start_x, start_y, end_x, end_y, strokeColor=colors.HexColor("#4472C4"), strokeWidth=1)
        )
    return drawing


def build_pdf() -> None:
    styles = report_styles()
    body = styles["BodySmall"]
    bullet = styles["BulletSmall"]
    story = []

    story.extend(
        [
            Spacer(1, 12 * mm),
            paragraph("Factori Mobile App Platform", styles["CoverTitle"]),
            paragraph("Architecture, security, delivery, and cost assessment", styles["Heading2"]),
            Spacer(1, 4 * mm),
            paragraph("Prepared 2026-11-18 | Prototype assessment | INR planning model", styles["BodyText"]),
            Spacer(1, 8 * mm),
            paragraph("Executive summary", styles["SectionTitle"]),
            paragraph(
                "The repository now contains an Android WebView template, local APK generator, "
                "tenant-scoped Spring Boot API prototype, PostgreSQL schema, and isolated build-worker "
                "definition. The API and Android build/tests pass; PostgreSQL 16.6 migration and forced "
                "RLS checks pass. This remains a proof of concept: no retailer dashboard, authoritative "
                "order connector, customer identity, FCM delivery, production signing, or durable build queue.",
                body,
            ),
            paragraph("Product decision", styles["SubTitle"]),
            paragraph(
                "Proceed only as a controlled pilot after Factori supplies retailer authorization, "
                "approved brand assets, authoritative eligibility data, signing ownership, and a distinct "
                "app value proposition. A WebView wrapper can be rejected by app stores even if its APK "
                "builds successfully.",
                body,
            ),
            make_table(
                [
                    ["Area", "Current evidence", "Release status"],
                    ["Android", "Target API 36; unit tests/build pass", "Device and store review pending"],
                    ["API", "JWT scopes, tenant scoping, async build API", "Order provider and durable queue absent"],
                    ["Database", "PostgreSQL 16.6 migration/RLS check", "Full API-backed integration tests pending"],
                    ["Build worker", "Constrained Docker invocation defined", "Image build blocked by local Docker overlay error"],
                    ["Artifacts", "Debug APK POCs; synthetic icons", "No approved logos or production signing"],
                ],
                [24 * mm, 74 * mm, 72 * mm],
            ),
            PageBreak(),
            paragraph("1. System architecture", styles["SectionTitle"]),
            architecture_diagram(),
            paragraph(
                "The public API validates configuration and tenant authorization, persists an app/build, "
                "and submits a bounded job. A trusted build host runs a network-disabled Docker worker. "
                "The worker stores APK and logs in a private local filesystem. Customer devices load the "
                "retailer's HTTPS storefront and hand off approved payment links.",
                body,
            ),
            paragraph("Trust boundaries", styles["SubTitle"]),
            *bullets(
                [
                    "Identity provider to API: verified JWT issuer, UUID tenant_id claim, and read/write scopes.",
                    "API to PostgreSQL: transaction-local tenant context and forced row-level security.",
                    "API/build host to Docker daemon: privileged boundary; keep the daemon off the public API plane.",
                    "Worker to build inputs: copied template, validated URL, icon, no network, resource caps, cleanup.",
                    "Android WebView to storefront: HTTPS origin allowlist, no JS bridge, external payment handoff.",
                ],
                bullet,
            ),
            PageBreak(),
            paragraph("2. Data model and tenant isolation", styles["SectionTitle"]),
            make_table(
                [
                    ["Entity group", "Purpose", "State"],
                    ["Retailers / apps", "Tenant root, app identity, URL, icon, app status/version", "Persisted and used"],
                    ["Builds / versions", "Queue/build status, artifact checksum, immutable successful version", "Persisted and used"],
                    ["Signing metadata", "Secret-manager reference and certificate hash; no key bytes", "Schema only"],
                    ["Users / installations", "Customer reference, consent, app install lifecycle", "Schema only"],
                    ["Device tokens", "Encrypted FCM token, HMAC lookup, key reference, consent", "Schema only"],
                    ["Campaigns / delivery", "App-scoped push payload and send state", "Schema only"],
                ],
                [36 * mm, 90 * mm, 44 * mm],
            ),
            Spacer(1, 4 * mm),
            paragraph(
                "All ten tables have tenant policies and FORCE ROW LEVEL SECURITY. Composite foreign keys "
                "bind cross-record relationships to the same retailer and, where applicable, the same app. "
                "The API derives tenant ID from JWT claims and sets app.tenant_id within each transaction. "
                "The schema was applied to PostgreSQL 16.6 with a separate migration role; a two-tenant "
                "check returned only the selected tenant row, zero rows without tenant context, and enforced "
                "RLS even for the table owner. PostgreSQL superusers and BYPASSRLS roles remain out of bounds.",
                body,
            ),
            paragraph(
                "Use a restricted migration role and a separate non-superuser runtime role. Flyway accepts "
                "MIGRATION_DATABASE_* overrides; the runtime datasource uses DATABASE_*.",
                body,
            ),
            PageBreak(),
            paragraph("3. Mobile app, API, and business rules", styles["SectionTitle"]),
            paragraph("Android client", styles["SubTitle"]),
            *bullets(
                [
                    "Configured app name, package ID, URL, icon, and version; debug APKs are not distributable releases.",
                    "JavaScript and DOM storage support storefront use; file access, mixed content, universal file URLs, and third-party cookies are disabled.",
                    "Reject TLS errors; constrain top-level browsing to retailer/payment hosts; use external browser/payment app handoff elsewhere.",
                    "Current template has no customer login, native cart/order API, push registration, or order synchronization.",
                ],
                bullet,
            ),
            paragraph("API", styles["SubTitle"]),
            paragraph(
                "GET /api/apps/eligibility and GET routes require mobile-apps:read; generation, regeneration, and "
                "other POST routes require mobile-apps:write. The API supports generation, list/status, download, "
                "regeneration, and eligibility. Uploads are capped at 5 MiB for the icon. Downloads use private, "
                "no-store headers and tenant-authorized lookups.",
                body,
            ),
            paragraph("Eligibility", styles["SubTitle"]),
            paragraph(
                "Prototype rule: eligible if completed orders >= 500 OR qualifying sales >= INR 250,000 since "
                "store launch. A measuredAt timestamp is required. With no authoritative provider bean, the API "
                "returns 503 instead of assuming eligibility. Refunds, cancellations, tax, discounts, attribution, "
                "and time-window definitions need Factori approval.",
                body,
            ),
            PageBreak(),
            paragraph("4. Security and privacy assessment", styles["SectionTitle"]),
            make_table(
                [
                    ["Control", "Implemented", "Remaining action"],
                    ["Authentication", "JWT issuer verification, tenant claim, endpoint scopes", "Provision users/roles and issuer policies"],
                    ["Tenant access", "Tenant-scoped service queries + forced RLS", "Add PostgreSQL API integration tests and audit alerts"],
                    ["WebView", "HTTPS, TLS rejection, origin allowlist, no bridge", "Device-test checkout, redirects, file upload, keyboard/insets"],
                    ["Build isolation", "No network, CPU/memory/PID caps, dropped capabilities, non-root", "Image scan/signing, dedicated patched worker host"],
                    ["Secrets/signing", "Database stores references, not key bytes", "KMS/HSM integration, custody, rotation, release approval"],
                    ["Privacy", "Consent/revocation fields in schema", "No live customer data until consent/deletion/retention flows exist"],
                ],
                [31 * mm, 61 * mm, 78 * mm],
            ),
            Spacer(1, 4 * mm),
            paragraph(
                "No rate limiting, tenant quotas, durable retries, object-store retention, notification consent "
                "flow, customer deletion, or production audit pipeline is present. The worker's Docker daemon "
                "access is privileged; isolating it is a deployment prerequisite. India privacy requirements "
                "were not independently verified in this assessment; obtain current legal review.",
                body,
            ),
            PageBreak(),
            paragraph("5. Deployment and operations", styles["SectionTitle"]),
            paragraph("Prototype topology", styles["SubTitle"]),
            *bullets(
                [
                    "Spring Boot 4.1.2 API + PostgreSQL; H2 is used only for context/unit tests.",
                    "Single-process bounded in-memory build queue (default two workers, queue capacity 20). Jobs can be lost on restart.",
                    "Private local filesystem for icons, APKs, and logs; no multi-instance replication or retention scheduler.",
                    "Prebuilt offline Android worker image; Docker host must be trusted and segregated from public traffic.",
                    "Local docker-compose starts PostgreSQL only on loopback; it does not expose a worker socket or start the API.",
                ],
                bullet,
            ),
            paragraph("Production evolution", styles["SubTitle"]),
            *bullets(
                [
                    "Add gateway rate limits, per-tenant quotas, request idempotency, durable queue, retries, dead-lettering, and reconciliation.",
                    "Move APK/icon/log storage to encrypted private object storage with lifecycle, integrity checks, and tenant-scoped URLs.",
                    "Use managed PostgreSQL, separate migration/runtime roles, backups, tested restore, monitoring, tracing, audit, and SLOs.",
                    "Build immutable/scanned worker images on dedicated hosts, use allowlisted inputs, and preserve no-egress execution.",
                    "Add FCM token enrollment, consent, encryption/key rotation, opt-out, invalid-token handling, and delivery reporting only after policy approval.",
                ],
                bullet,
            ),
            PageBreak(),
            paragraph("6. Illustrative cost model", styles["SectionTitle"]),
            paragraph(
                "The companion Excel workbook is editable and formula-driven. Values below illustrate scaling "
                "drivers only; they are not vendor quotes. INR 300,000/FTE-month, storage at INR 2/GB-month, "
                "key management at INR 30/app-month, APK size 60 MB, one build/app/month, and three retained "
                "versions are planning assumptions. Direct FCM message cost is modeled as zero; supporting "
                "compute, egress, adjacent services, tax, and operational overhead may still cost money.",
                body,
            ),
            make_table(
                [
                    ["Apps", "Approach", "Recurring/mo", "Setup", "Year 1 avg/mo"],
                    *[
                        [
                            f"{apps:,}",
                            approach["name"],
                            f"INR {calculate_cost(apps, approach)[0]:,.0f}",
                            f"INR {calculate_cost(apps, approach)[1]:,.0f}",
                            f"INR {(calculate_cost(apps, approach)[0] + calculate_cost(apps, approach)[1] / 12):,.0f}",
                        ]
                        for apps in APP_SCENARIOS
                        for approach in APPROACHES
                    ],
                ],
                [18 * mm, 35 * mm, 38 * mm, 38 * mm, 40 * mm],
                font_size=7.5,
            ),
            Spacer(1, 4 * mm),
            paragraph(
                "Assumptions omit quotes for hosting, managed database, monitoring, KMS, bandwidth, support, "
                "tax, store review, and engineering delivery. Replace every highlighted workbook input with "
                "region-specific Factori/vendor values before budget approval.",
                body,
            ),
            PageBreak(),
            paragraph("7. Delivery plan, risks, and open questions", styles["SectionTitle"]),
            make_table(
                [
                    ["Gate", "Work", "Exit evidence"],
                    ["1. Product inputs", "Map the 30-question brief; confirm brand assets, retailer authorization, payment host list", "Signed-off scope and app value proposition"],
                    ["2. Integrations", "Authoritative order API, identity/tenant provisioning, FCM and consent design", "Contract tests and approved data semantics"],
                    ["3. Platform hardening", "Durable queue, object storage, key custody, rate limits, observability, recovery", "Threat review, load/recovery tests, operational runbook"],
                    ["4. App/store readiness", "API 34 emulator/device matrix now; API 36 toolchain upgrade, checkout, accessibility, Play/Apple eligibility", "Retailer-signed acceptance and app-store review"],
                ],
                [28 * mm, 79 * mm, 71 * mm],
            ),
            Spacer(1, 4 * mm),
            paragraph(
                "The complete 30-question brief is now available. This assessment summarizes the major "
                "architecture and implementation findings, but is not yet a numbered, evidence-backed response "
                "to every question; several Factori-specific decisions and integrations remain open.",
                body,
            ),
            paragraph("External references (checked 2026-11-18)", styles["SubTitle"]),
            *bullets(
                [
                    '<link href="https://support.google.com/googleplay/android-developer/answer/11926878" color="blue">Google Play target API requirements</link>: API 36 required for new apps/updates from 2026-08-31.',
                    '<link href="https://support.google.com/googleplay/android-developer/answer/9899034" color="blue">Google Play spam/repetitive content policy</link>.',
                    '<link href="https://developer.apple.com/app-store/review/guidelines/" color="blue">Apple App Review Guidelines</link>, including 4.2 and 4.2.6.',
                    '<link href="https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion" color="blue">Android WebView security guidance</link>.',
                    '<link href="https://firebase.google.com/docs/cloud-messaging" color="blue">Firebase Cloud Messaging docs</link>.',
                ],
                bullet,
            ),
            paragraph(
                "See README.md and docs/ for API, schema, security, assumptions, and implementation status. "
                "No app-store approval, legal compliance, production signing, or production SLO is asserted.",
                body,
            ),
        ]
    )

    def footer(canvas, document):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor("#CAD4E0"))
        canvas.line(18 * mm, 13 * mm, 192 * mm, 13 * mm)
        canvas.setFont("Helvetica", 8)
        canvas.setFillColor(colors.HexColor("#666666"))
        canvas.drawString(18 * mm, 8 * mm, "Factori Mobile Platform | Prototype assessment")
        canvas.drawRightString(192 * mm, 8 * mm, f"Page {document.page}")
        canvas.restoreState()

    DOCS.mkdir(parents=True, exist_ok=True)
    document = SimpleDocTemplate(
        str(PDF_PATH),
        pagesize=A4,
        rightMargin=18 * mm,
        leftMargin=18 * mm,
        topMargin=16 * mm,
        bottomMargin=19 * mm,
        title="Factori Mobile App Platform Assessment",
        author="Factori",
    )
    document.build(story, onFirstPage=footer, onLaterPages=footer)


if __name__ == "__main__":
    build_workbook()
    build_pdf()
    print(f"Created {XLSX_PATH}")
    print(f"Created {PDF_PATH}")
