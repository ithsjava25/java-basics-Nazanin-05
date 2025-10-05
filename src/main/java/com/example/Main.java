package com.example;

import com.example.api.ElpriserAPI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: --zone SE1|SE2|SE3|SE4 [--date YYYY-MM-DD] [--sorted] [--charging 2h|4h|8h] [--help]");
            return;
        }

        ElpriserAPI elpriserAPI = new ElpriserAPI();

        String zone = "";
        LocalDate date = LocalDate.now();
        List<ElpriserAPI.Elpris> priser = new ArrayList<>();
        boolean sortedRequested = false;
        boolean helpRequested = false;

        // NumberFormat för svenska decimaltal
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("sv", "SE"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                    helpRequested = true;
                    break;

                case "--sorted":
                    sortedRequested = true;
                    break;

                case "--zone":
                    if (i + 1 < args.length) {
                        zone = args[i + 1].toUpperCase();
                        i++;
                    }
                    break;

                case "--date":
                    if (i + 1 < args.length) {
                        try {
                            date = LocalDate.parse(args[i + 1], DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                            i++;
                        } catch (DateTimeParseException e) {
                            System.out.println("Invalid date");
                            return;

                        }
                    }
                    break;
            }

                if (helpRequested) {
                    System.out.println ("--zone SE1|SE2|SE3|SE4 (required");
                    System.out.println ("--date YYYY-MM-DD (optional, defaults to current date");
                    System.out.println ("--sorted (optional, to display prices in descending order");
                    System.out.println ("--charging 2h|4h|8h (optional, to find optimal charging windows");
                    System.out.println("--help (optional, to display usage information");
                    return;
            }
            if (zone.isEmpty()) {
                System.out.println("Missing zone");
                return;

            }

            try {
                priser = elpriserAPI.getPriser(date, ElpriserAPI.Prisklass.valueOf(zone));
            } catch (IllegalArgumentException e) {
                System.out.println("Invalid zone");
                return;
            }

            if (!priser.isEmpty()) {

                ElpriserAPI.Elpris minPris = priser.get(0);
                ElpriserAPI.Elpris maxPris = priser.get(0);
                double sumPris = 0;

                for (ElpriserAPI.Elpris p : priser) {
                    double pris = p.sekPerKWh();
                    if (pris < minPris.sekPerKWh()) minPris = p;
                    if (pris > maxPris.sekPerKWh()) maxPris = p;
                    sumPris += pris;
                }
                double medelPris = sumPris / priser.size();

                System.out.println("Lägsta pris: " + nf.format(minPris.sekPerKWh() * 100)
                        + " öre (" + String.format("%02d", minPris.timeStart().getHour())
                        + "-" + String.format("%02d", minPris.timeEnd().getHour()) + ")");

                System.out.println("Högsta pris: " + nf.format(maxPris.sekPerKWh() * 100)
                        + " öre (" + String.format("%02d", maxPris.timeStart().getHour())
                        + "-" + String.format("%02d", maxPris.timeEnd().getHour()) + ")");

                System.out.println("Medelpris: " + nf.format(medelPris * 100) + " öre");

                System.out.println("Medelpris: "
                        + (Math.round(medelPris * 1000) / 10.0)
                        + " öre");

                if (sortedRequested) {
                    List<ElpriserAPI.Elpris> sortedPriser = new ArrayList<>(priser);
                    sortedPriser.sort(Comparator.comparingDouble(ElpriserAPI.Elpris::sekPerKWh));

                    for (ElpriserAPI.Elpris p : sortedPriser) {
                        String timeRange = String.format("%02d-%02d", p.timeStart().getHour(), p.timeEnd().getHour());
                        String priceOre = nf.format(p.sekPerKWh() * 100); // NYTT
                        System.out.println(timeRange + " " + priceOre + " öre");}
                }

            }
            else {
                System.out.println("Inga priser tillgängliga");

            }}

         if (Arrays.asList(args).contains("--charging")) {
                int windowHours = 0;
                for (int j = 0; j < args.length; j++) {
                    if ("--charging".equals(args[j]) && j + 1 < args.length) {
                        String val = args[j + 1].toLowerCase();
                        switch (val) {
                            case "2h" -> windowHours = 2;
                            case "4h" -> windowHours = 4;
                            case "8h" -> windowHours = 8;
                        }
                    }
                }

            if (windowHours > 0) {
                System.out.println("Påbörja laddning");

                List<ElpriserAPI.Elpris> allPrices = new ArrayList<>(priser);

                try {
                    List<ElpriserAPI.Elpris> nextDayPrices = elpriserAPI.getPriser(date.plusDays(1), ElpriserAPI.Prisklass.valueOf(zone));
                    allPrices.addAll(nextDayPrices);
                } catch (IllegalArgumentException e) {
                    // Fångar undantag medvetet, eftersom vi bara vill fortsätta med befintliga priser

                }

                // Loop för att hitta billigaste fönster
                double minTotal = Double.MAX_VALUE;
                int startIndex = 0;

                for (int k = 0; k <= allPrices.size() - windowHours; k++) {
                    double total = 0;
                    for (int j = 0; j < windowHours; j++) {
                        total += allPrices.get(k + j).sekPerKWh() * 100; // Öre
                    }
                    if (total < minTotal) {
                        minTotal = total;
                        startIndex = k;
                    }
                }
                // Skriv ut start och slut på fönstret
                ElpriserAPI.Elpris start = allPrices.get(startIndex);
                ElpriserAPI.Elpris end = allPrices.get(startIndex + windowHours - 1);

                if (allPrices.size() >= startIndex + windowHours) {
                String startTime = String.format("%02d:%02d", start.timeStart().getHour(), start.timeStart().getMinute());
                String endTime = String.format("%02d:%02d", end.timeEnd().getHour(), end.timeEnd().getMinute());

                System.out.println("Billigaste fönster: " + startTime + "-" + endTime
                        + " Totalkostnad: " + String.format("%.2f", minTotal).replace('.', ',') + " öre");


                System.out.println("Påbörja laddning kl " + startTime);

                    double windowMean = minTotal / windowHours; // medelpris i öre
                String windowMeanStr = String.format("%.2f", windowMean).replace('.', ',');
                System.out.println("Medelpris för fönster: " + windowMeanStr + " öre");
                }}
        }
    } }