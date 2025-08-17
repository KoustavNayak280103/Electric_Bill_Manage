import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class electricbillmanagement {

    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String CONSUMERS_FILE = "consumers.dat";
    private static final String READINGS_FILE  = "readings.dat";
    private static final String BILLS_FILE     = "bills.dat";

    // In-memory stores
    private Map<Integer, Consumer> consumers = new TreeMap<>();
    private Map<Integer, List<MeterReading>> readings = new TreeMap<>(); // consumerId -> readings list (sorted)
    private Map<Integer, Bill> bills = new TreeMap<>(); // billId -> bill

    private int consumerCounter = 1;
    private int billCounter = 1;

    // Default tariff config (can be changed via menu)
    private Tariff tariff = Tariff.defaultTariff();

    public static void main(String[] args) {
        electricbillmanagement app = new electricbillmanagement();
        app.loadData();
        app.bootstrapSampleIfEmpty();
        app.run();
        app.saveData();
        System.out.println("Exiting. Data saved.");
    }

    // ---------- MAIN MENU ----------
    private void run() {
        while (true) {
            System.out.println("\n=== Electricity Bill Control System ===");
            System.out.println("Tariff summary: " + tariff.summary());
            System.out.println("1. Manage Consumers");
            System.out.println("2. Meter Readings");
            System.out.println("3. Generate Bills (for month)");
            System.out.println("4. View / Pay Bills");
            System.out.println("5. Reports");
            System.out.println("6. Tariff Settings");
            System.out.println("7. Save Data");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": consumersMenu(); break;
                case "2": readingsMenu(); break;
                case "3": generateBillsMenu(); break;
                case "4": billsMenu(); break;
                case "5": reportsMenu(); break;
                case "6": tariffMenu(); break;
                case "7": saveData(); System.out.println("Data saved."); break;
                case "0": return;
                default: System.out.println("Invalid choice."); break;
            }
        }
    }

    // ---------- CONSUMERS ----------
    private void consumersMenu() {
        while (true) {
            System.out.println("\n--- Consumers ---");
            System.out.println("1. Add Consumer");
            System.out.println("2. List Consumers");
            System.out.println("3. Update Consumer");
            System.out.println("4. Delete Consumer");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": addConsumer(); break;
                case "2": listConsumers(); break;
                case "3": updateConsumer(); break;
                case "4": deleteConsumer(); break;
                case "0": return;
                default: System.out.println("Invalid."); break;
            }
        }
    }

    private void addConsumer() {
        System.out.println("\nAdd Consumer");
        System.out.print("Name: "); String name = scanner.nextLine().trim();
        System.out.print("Address: "); String address = scanner.nextLine().trim();
        System.out.print("Phone: "); String phone = scanner.nextLine().trim();
        System.out.print("Meter Number: "); String meter = scanner.nextLine().trim();
        Consumer c = new Consumer(consumerCounter++, name, address, phone, meter, LocalDate.now());
        consumers.put(c.getId(), c);
        System.out.println("Consumer added with ID: " + c.getId());
    }

    private void listConsumers() {
        if (consumers.isEmpty()) { System.out.println("No consumers."); return; }
        System.out.printf("\n%-4s %-25s %-15s %-12s %-10s%n", "ID", "Name", "Phone", "Meter#", "Joined");
        for (Consumer c : consumers.values()) {
            System.out.printf("%-4d %-25s %-15s %-12s %-10s%n", c.getId(), c.getName(), optional(c.getPhone()), optional(c.getMeterNumber()), c.getCreatedAt().toString());
        }
    }

    private void updateConsumer() {
        int id = promptInt("Consumer ID: ");
        Consumer c = consumers.get(id);
        if (c == null) { System.out.println("Not found."); return; }
        System.out.println("Leave blank to keep value.");
        System.out.print("Name ("+c.getName()+"): "); String name = scanner.nextLine().trim();
        System.out.print("Address ("+optional(c.getAddress())+"): "); String addr = scanner.nextLine().trim();
        System.out.print("Phone ("+optional(c.getPhone())+"): "); String phone = scanner.nextLine().trim();
        System.out.print("Meter# ("+optional(c.getMeterNumber())+"): "); String meter = scanner.nextLine().trim();
        if (!name.isEmpty()) c.setName(name);
        if (!addr.isEmpty()) c.setAddress(addr);
        if (!phone.isEmpty()) c.setPhone(phone);
        if (!meter.isEmpty()) c.setMeterNumber(meter);
        System.out.println("Updated.");
    }

    private void deleteConsumer() {
        int id = promptInt("Consumer ID to delete: ");
        if (!consumers.containsKey(id)) { System.out.println("Not found."); return; }
        boolean hasBills = bills.values().stream().anyMatch(b -> b.getConsumerId() == id);
        if (hasBills) { System.out.println("Cannot delete consumer with bills. Remove bills first."); return; }
        consumers.remove(id);
        readings.remove(id);
        System.out.println("Deleted.");
    }

    // ---------- METER READINGS ----------
    private void readingsMenu() {
        while (true) {
            System.out.println("\n--- Meter Readings ---");
            System.out.println("1. Add Reading");
            System.out.println("2. List Readings (consumer)");
            System.out.println("3. Import sample readings");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": addReading(); break;
                case "2": listReadingsForConsumer(); break;
                case "3": importSampleReadings(); break;
                case "0": return;
                default: System.out.println("Invalid."); break;
            }
        }
    }

    private void addReading() {
        int cid = promptInt("Consumer ID: ");
        if (!consumers.containsKey(cid)) { System.out.println("Consumer not found."); return; }
        System.out.print("Reading date-time (yyyy-MM-dd HH:mm) or blank = now: ");
        String dt = scanner.nextLine().trim();
        LocalDateTime when;
        if (dt.isEmpty()) when = LocalDateTime.now();
        else {
            try { when = LocalDateTime.parse(dt, DT); } catch (Exception ex) { System.out.println("Invalid format."); return; }
        }
        int units = promptInt("Meter reading (cumulative units): ");
        MeterReading r = new MeterReading(cid, when, units);
        readings.computeIfAbsent(cid, k -> new ArrayList<>()).add(r);
        // sort by date
        readings.get(cid).sort(Comparator.comparing(MeterReading::getWhen));
        System.out.println("Reading saved.");
    }

    private void listReadingsForConsumer() {
        int cid = promptInt("Consumer ID: ");
        if (!consumers.containsKey(cid)) { System.out.println("Not found."); return; }
        List<MeterReading> list = readings.getOrDefault(cid, Collections.emptyList());
        if (list.isEmpty()) { System.out.println("No readings."); return; }
        System.out.printf("\n%-20s %-10s%n", "When", "Units");
        for (MeterReading r : list) System.out.printf("%-20s %-10d%n", r.getWhen().format(DT), r.getUnits());
    }

    private void importSampleReadings() {
        // small helper to create a few readings per consumer (for demo)
        for (Integer cid : consumers.keySet()) {
            List<MeterReading> list = readings.computeIfAbsent(cid, k -> new ArrayList<>());
            LocalDateTime base = LocalDateTime.now().minusMonths(6);
            int baseUnits = 1000 + cid * 50;
            for (int m = 0; m < 6; m++) {
                base = base.plusMonths(1);
                baseUnits += 80 + (cid % 5) * 10;
                list.add(new MeterReading(cid, base, baseUnits));
            }
            list.sort(Comparator.comparing(MeterReading::getWhen));
        }
        System.out.println("Sample readings imported for all consumers.");
    }

    // ---------- BILL GENERATION ----------
    private void generateBillsMenu() {
        System.out.println("\nGenerate bills for month (year-month). Example: 2025-08");
        System.out.print("Enter year-month or blank = current month: ");
        String ym = scanner.nextLine().trim();
        YearMonth target;
        if (ym.isEmpty()) target = YearMonth.now();
        else {
            try { target = YearMonth.parse(ym); } catch (Exception ex) { System.out.println("Invalid format."); return; }
        }
        int generated = 0;
        for (Consumer c : consumers.values()) {
            if (generateBillForConsumerForMonth(c.getId(), target)) generated++;
        }
        System.out.println("Generated/updated bills for "+generated+" consumers for " + target);
    }

    // generate bill, returns true if bill created/updated
    private boolean generateBillForConsumerForMonth(int consumerId, YearMonth month) {
        List<MeterReading> list = readings.getOrDefault(consumerId, Collections.emptyList());
        if (list.size() < 2) {
            // if fewer readings, cannot compute consumption reliably; skip
            return false;
        }
        // find reading at or before start of month (latest before month start)
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.atEndOfMonth().atTime(23,59,59);
        MeterReading before = null, after = null;
        for (MeterReading r : list) {
            if (!r.getWhen().isAfter(start)) before = r;
            if (!r.getWhen().isBefore(end)) { after = r; break; } // first reading in/after end
        }
        // fallback: consider nearest before start as before, and nearest after start as after
        if (before == null) {
            // use earliest reading before the first reading available for consumer
            before = list.get(0);
        }
        if (after == null) {
            // try last reading in range or last reading overall
            Optional<MeterReading> lastInRange = list.stream().filter(r -> !r.getWhen().isBefore(start) && !r.getWhen().isAfter(end)).findFirst();
            if (lastInRange.isPresent()) after = lastInRange.get();
            else after = list.get(list.size()-1);
        }
        // If after's units < before's units, skip (meter reset?) — treat as not billable
        if (after.getUnits() < before.getUnits()) return false;
        int consumed = after.getUnits() - before.getUnits();

        // compute charges
        double energyCharge = tariff.calculate(consumed);
        double fixed = tariff.getFixedCharge();
        double subtotal = energyCharge + fixed;
        double tax = subtotal * tariff.getTaxRate();
        double total = subtotal + tax;

        // check if bill already exists for this consumer & month
        Bill existing = bills.values().stream()
                .filter(b -> b.getConsumerId() == consumerId && b.getYearMonth().equals(month))
                .findFirst().orElse(null);

        if (existing != null) {
            // update existing bill details (if unpaid)
            if (existing.isPaid()) return false; // don't overwrite paid bills
            existing.setUnits(consumed);
            existing.setEnergyCharge(energyCharge);
            existing.setFixedCharge(fixed);
            existing.setTax(tax);
            existing.setTotal(total);
            existing.setGeneratedAt(LocalDateTime.now());
        } else {
            Bill b = new Bill(billCounter++, consumerId, month, consumed, energyCharge, fixed, tariff.getTaxRate(), tax, total, LocalDateTime.now());
            bills.put(b.getId(), b);
        }
        return true;
    }

    // ---------- BILLS & PAYMENTS ----------
    private void billsMenu() {
        while (true) {
            System.out.println("\n--- Bills ---");
            System.out.println("1. List Bills");
            System.out.println("2. View Bill");
            System.out.println("3. Pay Bill");
            System.out.println("4. Regenerate month bills");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": listBills(); break;
                case "2": viewBill(); break;
                case "3": payBill(); break;
                case "4": regenerateMenu(); break;
                case "0": return;
                default: System.out.println("Invalid."); break;
            }
        }
    }

    private void listBills() {
        if (bills.isEmpty()) { System.out.println("No bills."); return; }
        System.out.printf("\n%-4s %-8s %-20s %-8s %-8s %-8s %-6s%n", "ID", "Period", "Consumer", "Units", "Total", "Paid", "Date");
        List<Bill> list = bills.values().stream().sorted(Comparator.comparing(Bill::getYearMonth).reversed()).collect(Collectors.toList());
        for (Bill b : list) {
            Consumer c = consumers.get(b.getConsumerId());
            System.out.printf("%-4d %-8s %-20s %-8d %-8.2f %-8s %-6s%n",
                    b.getId(), b.getYearMonth(), c != null ? c.getName() : "[unknown]", b.getUnits(),
                    b.getTotal(), b.isPaid() ? "YES" : "NO", b.getGeneratedAt().toLocalDate().toString());
        }
    }

    private void viewBill() {
        int id = promptInt("Bill ID: ");
        Bill b = bills.get(id);
        if (b == null) { System.out.println("Not found."); return; }
        Consumer c = consumers.get(b.getConsumerId());
        System.out.println("\n----- BILL -----");
        System.out.println("Bill ID: " + b.getId());
        System.out.println("Period: " + b.getYearMonth());
        System.out.println("Consumer: " + (c != null ? c.getName() + " (ID " + c.getId() + ")" : "[unknown]"));
        System.out.println("Units consumed: " + b.getUnits());
        System.out.printf("Energy charge: %.2f%n", b.getEnergyCharge());
        System.out.printf("Fixed charge:  %.2f%n", b.getFixedCharge());
        System.out.printf("Tax (%.2f%%):   %.2f%n", b.getTaxRate() * 100.0, b.getTax());
        System.out.printf("TOTAL:         %.2f%n", b.getTotal());
        System.out.println("Status: " + (b.isPaid() ? "PAID on " + b.getPaidAt().format(DT) : "UNPAID"));
    }

    private void payBill() {
        int id = promptInt("Bill ID to pay: ");
        Bill b = bills.get(id);
        if (b == null) { System.out.println("Not found."); return; }
        if (b.isPaid()) { System.out.println("Already paid on " + b.getPaidAt().format(DT)); return; }
        System.out.printf("Amount due: %.2f. Confirm payment? (y/n): ", b.getTotal());
        String ans = scanner.nextLine().trim().toLowerCase();
        if (ans.equals("y") || ans.equals("yes")) {
            b.setPaid(true);
            b.setPaidAt(LocalDateTime.now());
            System.out.println("Payment recorded.");
        } else System.out.println("Cancelled.");
    }

    private void regenerateMenu() {
        System.out.print("Regenerate for year-month (yyyy-MM) or blank=current: ");
        String ym = scanner.nextLine().trim();
        YearMonth target;
        if (ym.isEmpty()) target = YearMonth.now();
        else {
            try { target = YearMonth.parse(ym); } catch (Exception ex) { System.out.println("Invalid."); return; }
        }
        int updated = 0;
        for (Integer cid : consumers.keySet()) if (generateBillForConsumerForMonth(cid, target)) updated++;
        System.out.println("Regenerated " + updated + " bills.");
    }

    // ---------- REPORTS ----------
    private void reportsMenu() {
        while (true) {
            System.out.println("\n--- Reports ---");
            System.out.println("1. Outstanding balances");
            System.out.println("2. Bills by date range");
            System.out.println("3. Consumption summary (by consumer)");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": reportOutstanding(); break;
                case "2": reportBillsByRange(); break;
                case "3": reportConsumptionSummary(); break;
                case "0": return;
                default: System.out.println("Invalid."); break;
            }
        }
    }

    private void reportOutstanding() {
        System.out.println("\nOutstanding (unpaid) bills:");
        List<Bill> unpaid = bills.values().stream().filter(b -> !b.isPaid()).sorted(Comparator.comparing(Bill::getYearMonth)).collect(Collectors.toList());
        if (unpaid.isEmpty()) { System.out.println("No outstanding bills."); return; }
        System.out.printf("%-4s %-8s %-20s %-8s %-8s%n", "ID", "Period", "Consumer", "Units", "Total");
        for (Bill b : unpaid) {
            Consumer c = consumers.get(b.getConsumerId());
            System.out.printf("%-4d %-8s %-20s %-8d %-8.2f%n", b.getId(), b.getYearMonth(), c != null ? c.getName() : "[unknown]", b.getUnits(), b.getTotal());
        }
    }

    private void reportBillsByRange() {
        System.out.print("Start date (yyyy-MM-dd): ");
        String s1 = scanner.nextLine().trim();
        System.out.print("End date (yyyy-MM-dd): ");
        String s2 = scanner.nextLine().trim();
        LocalDate start, end;
        try { start = LocalDate.parse(s1); end = LocalDate.parse(s2); } catch (Exception ex) { System.out.println("Invalid date format."); return; }
        System.out.println("\nBills generated between " + start + " and " + end + ":");
        List<Bill> list = bills.values().stream()
                .filter(b -> {
                    LocalDate d = b.getGeneratedAt().toLocalDate();
                    return (!d.isBefore(start)) && (!d.isAfter(end));
                }).sorted(Comparator.comparing(Bill::getGeneratedAt)).collect(Collectors.toList());
        if (list.isEmpty()) { System.out.println("No bills."); return; }
        for (Bill b : list) {
            Consumer c = consumers.get(b.getConsumerId());
            System.out.printf("Bill %d | %s | %s | Units: %d | Total: %.2f | Paid: %s%n",
                    b.getId(), b.getGeneratedAt().format(DT), c != null ? c.getName() : "[unknown]", b.getUnits(), b.getTotal(), b.isPaid()? "YES": "NO");
        }
    }

    private void reportConsumptionSummary() {
        System.out.print("Consumer ID (or blank for all): ");
        String s = scanner.nextLine().trim();
        if (s.isEmpty()) {
            System.out.println("\nConsumption summary for all consumers (last 6 readings):");
            for (Integer cid : consumers.keySet()) {
                int cons = totalConsumptionForConsumer(cid);
                System.out.printf("ID %d | %s | Total units (recent): %d%n", cid, consumers.get(cid).getName(), cons);
            }
        } else {
            try {
                int cid = Integer.parseInt(s);
                List<MeterReading> list = readings.getOrDefault(cid, Collections.emptyList());
                if (list.size() < 2) { System.out.println("Not enough readings."); return; }
                System.out.println("\nReadings:");
                list.forEach(r -> System.out.printf("%s => %d%n", r.getWhen().format(DT), r.getUnits()));
                System.out.println("Total consumption (first->last): " + (list.get(list.size()-1).getUnits() - list.get(0).getUnits()));
            } catch (Exception ex) { System.out.println("Invalid input."); }
        }
    }

    // ---------- TARIFF SETTINGS ----------
    private void tariffMenu() {
        while (true) {
            System.out.println("\n--- Tariff Settings ---");
            System.out.println("Current: " + tariff.summary());
            System.out.println("1. Edit slabs");
            System.out.println("2. Edit fixed charge");
            System.out.println("3. Edit tax rate");
            System.out.println("0. Back");
            System.out.print("Choose: ");
            String c = scanner.nextLine().trim();
            switch (c) {
                case "1": editSlabs(); break;
                case "2": editFixedCharge(); break;
                case "3": editTaxRate(); break;
                case "0": return;
                default: System.out.println("Invalid."); break;
            }
        }
    }

    private void editSlabs() {
        System.out.println("Slabs are ordered ranges [0..n) units with price per unit.");
        tariff.printSlabs();
        System.out.println("You can replace slabs with a comma-separated list of pairs unit:price e.g. 100:3.5,200:4.5,inf:6.0");
        System.out.print("Enter slabs: ");
        String s = scanner.nextLine().trim();
        if (s.isEmpty()) return;
        try {
            tariff = Tariff.parseFromString(s, tariff.getFixedCharge(), tariff.getTaxRate());
            System.out.println("Updated slabs.");
        } catch (Exception ex) {
            System.out.println("Parse error: " + ex.getMessage());
        }
    }

    private void editFixedCharge() {
        System.out.print("Enter fixed charge amount: ");
        String s = scanner.nextLine().trim();
        try { double v = Double.parseDouble(s); tariff.setFixedCharge(v); System.out.println("Updated."); } catch (Exception ex) { System.out.println("Invalid."); }
    }

    private void editTaxRate() {
        System.out.print("Enter tax rate (e.g. 0.05 for 5%): ");
        String s = scanner.nextLine().trim();
        try { double v = Double.parseDouble(s); tariff.setTaxRate(v); System.out.println("Updated."); } catch (Exception ex) { System.out.println("Invalid."); }
    }

    // ---------- PERSISTENCE ----------
    private void saveData() {
        try { writeObject(CONSUMERS_FILE, consumers); } catch (Exception ex) { System.out.println("Save consumers failed: " + ex.getMessage()); }
        try { writeObject(READINGS_FILE, readings); } catch (Exception ex) { System.out.println("Save readings failed: " + ex.getMessage()); }
        try { writeObject(BILLS_FILE, bills); } catch (Exception ex) { System.out.println("Save bills failed: " + ex.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        try {
            Object o = readObject(CONSUMERS_FILE);
            if (o != null) { consumers = (Map<Integer, Consumer>) o; consumerCounter = consumers.keySet().stream().mapToInt(i->i).max().orElse(0) + 1; }
        } catch (Exception ex) { /* ignore */ }
        try {
            Object o = readObject(READINGS_FILE);
            if (o != null) readings = (Map<Integer, List<MeterReading>>) o;
        } catch (Exception ex) { /* ignore */ }
        try {
            Object o = readObject(BILLS_FILE);
            if (o != null) { bills = (Map<Integer, Bill>) o; billCounter = bills.keySet().stream().mapToInt(i->i).max().orElse(0) + 1; }
        } catch (Exception ex) { /* ignore */ }
    }

    private void writeObject(String filename, Object obj) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(obj);
        }
    }

    private Object readObject(String filename) throws IOException, ClassNotFoundException {
        File f = new File(filename);
        if (!f.exists()) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            return ois.readObject();
        }
    }

    // ---------- UTIL ----------
    private static String optional(String s) { return s == null || s.isEmpty() ? "-" : s; }
    private static int promptInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim();
            try { return Integer.parseInt(s); } catch (Exception ex) { System.out.println("Invalid number."); }
        }
    }
    private int totalConsumptionForConsumer(int cid) {
        List<MeterReading> list = readings.getOrDefault(cid, Collections.emptyList());
        if (list.size() < 2) return 0;
        return list.get(list.size()-1).getUnits() - list.get(0).getUnits();
    }

    private void bootstrapSampleIfEmpty() {
        if (consumers.isEmpty()) {
            Consumer a = new Consumer(consumerCounter++, "Aman Sharma", "Mumbai", "9876500001", "MTR-1001", LocalDate.now().minusYears(1));
            Consumer b = new Consumer(consumerCounter++, "Seema Roy", "Delhi", "9876500002", "MTR-1002", LocalDate.now().minusYears(1));
            consumers.put(a.getId(), a); consumers.put(b.getId(), b);
            readings.put(a.getId(), new ArrayList<>());
            readings.put(b.getId(), new ArrayList<>());
            // add sample readings 6 months
            LocalDateTime base = LocalDateTime.now().minusMonths(6);
            int u1 = 1000, u2 = 800;
            for (int i=0;i<6;i++){
                base = base.plusMonths(1);
                u1 += 120; u2 += 90;
                readings.get(a.getId()).add(new MeterReading(a.getId(), base, u1));
                readings.get(b.getId()).add(new MeterReading(b.getId(), base, u2));
            }
            System.out.println("Sample consumers & readings created.");
        }
    }

    // ------------------ MODELS ------------------

    private static class Consumer implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private String name;
        private String address;
        private String phone;
        private String meterNumber;
        private LocalDate createdAt;

        public Consumer(int id, String name, String address, String phone, String meterNumber, LocalDate createdAt) {
            this.id = id; this.name = name; this.address = address; this.phone = phone; this.meterNumber = meterNumber; this.createdAt = createdAt;
        }
        public int getId(){ return id; }
        public String getName(){ return name; }
        public String getAddress(){ return address; }
        public String getPhone(){ return phone; }
        public String getMeterNumber(){ return meterNumber; }
        public LocalDate getCreatedAt(){ return createdAt; }
        public void setName(String s){ this.name = s; }
        public void setAddress(String s){ this.address = s; }
        public void setPhone(String s){ this.phone = s; }
        public void setMeterNumber(String s){ this.meterNumber = s; }
    }

    private static class MeterReading implements Serializable {
        private static final long serialVersionUID = 1L;
        private int consumerId;
        private LocalDateTime when;
        private int units; // cumulative units

        public MeterReading(int consumerId, LocalDateTime when, int units) {
            this.consumerId = consumerId; this.when = when; this.units = units;
        }
        public int getConsumerId(){ return consumerId; }
        public LocalDateTime getWhen(){ return when; }
        public int getUnits(){ return units; }
    }

    private static class Bill implements Serializable {
        private static final long serialVersionUID = 1L;
        private int id;
        private int consumerId;
        private YearMonth yearMonth;
        private int units;
        private double energyCharge;
        private double fixedCharge;
        private double taxRate;
        private double tax;
        private double total;
        private LocalDateTime generatedAt;

        // payment
        private boolean paid;
        private LocalDateTime paidAt;

        public Bill(int id, int consumerId, YearMonth yearMonth, int units, double energyCharge, double fixedCharge, double taxRate, double tax, double total, LocalDateTime generatedAt) {
            this.id = id; this.consumerId = consumerId; this.yearMonth = yearMonth; this.units = units;
            this.energyCharge = energyCharge; this.fixedCharge = fixedCharge; this.taxRate = taxRate; this.tax = tax; this.total = total; this.generatedAt = generatedAt;
            this.paid = false; this.paidAt = null;
        }
        public int getId(){ return id; }
        public int getConsumerId(){ return consumerId; }
        public YearMonth getYearMonth(){ return yearMonth; }
        public int getUnits(){ return units; }
        public double getEnergyCharge(){ return energyCharge; }
        public double getFixedCharge(){ return fixedCharge; }
        public double getTaxRate(){ return taxRate; }
        public double getTax(){ return tax; }
        public double getTotal(){ return total; }
        public LocalDateTime getGeneratedAt(){ return generatedAt; }
        public boolean isPaid(){ return paid; }
        public LocalDateTime getPaidAt(){ return paidAt; }

        public void setUnits(int u){ this.units = u; }
        public void setEnergyCharge(double v){ this.energyCharge = v; }
        public void setFixedCharge(double v){ this.fixedCharge = v; }
        public void setTaxRate(double v){ this.taxRate = v; }
        public void setTax(double v){ this.tax = v; }
        public void setTotal(double v){ this.total = v; }
        public void setGeneratedAt(LocalDateTime t){ this.generatedAt = t; }
        public void setPaid(boolean p){ this.paid = p; if (!p) this.paidAt = null; }
        public void setPaidAt(LocalDateTime t){ this.paidAt = t; this.paid = true; }
    }

    // Tariff: list of slabs (units, price per unit). slabUnits==Integer.MAX_VALUE means up-to-infinite
    private static class Tariff implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<Slab> slabs = new ArrayList<>();
        private double fixedCharge = 50.0;
        private double taxRate = 0.05; // 5%

        public static Tariff defaultTariff() {
            Tariff t = new Tariff();
            t.slabs.add(new Slab(100, 3.5));
            t.slabs.add(new Slab(200, 4.5)); // next 200
            t.slabs.add(new Slab(Integer.MAX_VALUE, 6.0)); // remaining
            t.fixedCharge = 50.0;
            t.taxRate = 0.05;
            return t;
        }

        // Calculate energy charge for given units
        public double calculate(int units) {
            int remaining = units;
            double charge = 0.0;
            for (Slab s : slabs) {
                if (remaining <= 0) break;
                int take = Math.min(remaining, s.units == Integer.MAX_VALUE ? remaining : s.units);
                charge += (double) take * s.rate;
                if (s.units != Integer.MAX_VALUE) {
                    remaining -= take;
                } else remaining = 0;
            }
            return charge;
        }

        public void setFixedCharge(double f){ this.fixedCharge = f; }
        public void setTaxRate(double t){ this.taxRate = t; }
        public double getFixedCharge(){ return fixedCharge; }
        public double getTaxRate(){ return taxRate; }

        public void printSlabs() {
            for (Slab s : slabs) {
                System.out.println(" - " + (s.units == Integer.MAX_VALUE ? "above" : s.units) + " units @ " + s.rate + "/unit");
            }
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            for (Slab s : slabs) {
                if (s.units == Integer.MAX_VALUE) sb.append("[above:").append(s.rate).append("]");
                else sb.append("[").append(s.units).append(":").append(s.rate).append("]");
            }
            sb.append(" fixed:").append(fixedCharge).append(" tax:").append(taxRate);
            return sb.toString();
        }

        public static Tariff parseFromString(String s, double fixed, double tax) {
            // format: 100:3.5,200:4.5,inf:6.0  OR use 'inf' or 'inf' or 'infty'
            Tariff t = new Tariff();
            t.fixedCharge = fixed; t.taxRate = tax;
            String[] parts = s.split(",");
            for (String p : parts) {
                String[] kv = p.trim().split(":");
                if (kv.length != 2) throw new IllegalArgumentException("Bad slab part: " + p);
                String u = kv[0].trim();
                String r = kv[1].trim();
                int units;
                if (u.equalsIgnoreCase("inf") || u.equalsIgnoreCase("infty") || u.equalsIgnoreCase("above")) units = Integer.MAX_VALUE;
                else units = Integer.parseInt(u);
                double rate = Double.parseDouble(r);
                t.slabs.add(new Slab(units, rate));
            }
            return t;
        }

        private static class Slab implements Serializable {
            private static final long serialVersionUID = 1L;
            int units;
            double rate;
            Slab(int units, double rate){ this.units = units; this.rate = rate; }
        }
    }
}