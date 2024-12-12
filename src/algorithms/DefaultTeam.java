package algorithms;

import java.awt.Point;
import java.util.*;

public class DefaultTeam {

    // Main Method to Compute Dominating Set
    public ArrayList<Point> calculDominatingSet(ArrayList<Point> points, int edgeThreshold) {
        ArrayList<Point> dominatingSet = new ArrayList<>();
        HashSet<Point> uncovered = new HashSet<>(points);

        // Improved Greedy Algorithm with Weighted Selection
        while (!uncovered.isEmpty()) {
            Point bestPoint = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Point candidate : uncovered) {
                int newCoverage = countNewCoverage(candidate, uncovered, edgeThreshold);
                int overlap = countOverlap(candidate, uncovered, edgeThreshold);

                // Weighted score: prioritize high coverage and low overlap
                double score = newCoverage - 0.5 * overlap;
                if (score > bestScore) {
                    bestScore = score;
                    bestPoint = candidate;
                }
            }

            if (bestPoint != null) {
                dominatingSet.add(bestPoint);
                updateUncovered(bestPoint, uncovered, edgeThreshold);
            }
        }

        // Post-processing with Local Search
        refineDominatingSet(dominatingSet, points, edgeThreshold);

        return dominatingSet;
    }

    // Count new vertices covered by adding candidate to the Dominating Set
    private int countNewCoverage(Point candidate, Set<Point> uncovered, int edgeThreshold) {
        int count = 0;
        for (Point p : uncovered) {
            if (candidate.distance(p) <= edgeThreshold) {
                count++;
            }
        }
        return count;
    }

    // Count overlap: how many already covered points the candidate dominates
    private int countOverlap(Point candidate, Set<Point> uncovered, int edgeThreshold) {
        int count = 0;
        for (Point p : uncovered) {
            if (candidate.distance(p) <= edgeThreshold) {
                count++;
            }
        }
        return count;
    }

    // Update uncovered set after adding a vertex to the Dominating Set
    private void updateUncovered(Point dominator, Set<Point> uncovered, int edgeThreshold) {
        uncovered.removeIf(p -> dominator.distance(p) <= edgeThreshold);
    }

    // Refine Dominating Set using Local Search
    private void refineDominatingSet(ArrayList<Point> dominatingSet, ArrayList<Point> points, int edgeThreshold) {
        for (int i = 0; i < dominatingSet.size(); i++) {
            Point toReplace = dominatingSet.get(i);

            for (Point candidate : points) {
                if (!dominatingSet.contains(candidate) && 
                    canReplacePoint(toReplace, candidate, dominatingSet, points, edgeThreshold)) {
                    dominatingSet.set(i, candidate);
                    break;
                }
            }
        }
    }

    // Check if a point can replace another in the Dominating Set
    private boolean canReplacePoint(Point toReplace, Point candidate, ArrayList<Point> dominatingSet, ArrayList<Point> points, int edgeThreshold) {
        HashSet<Point> coverWithCandidate = new HashSet<>();
        
        for (Point dominator : dominatingSet) {
            Point effective = dominator.equals(toReplace) ? candidate : dominator;
            for (Point p : points) {
                if (effective.distance(p) <= edgeThreshold) {
                    coverWithCandidate.add(p);
                }
            }
        }

        return coverWithCandidate.containsAll(points);
    }

    // Optimized Random Points Generator Logic Integration
    public ArrayList<Point> generateRandomPoints(int numberOfPoints, int maxWidth, int maxHeight, int radius, int edgeThreshold) {
        ArrayList<Point> points = new ArrayList<>();
        Random generator = new Random();

        int gridSize = edgeThreshold;
        int cols = (maxWidth + gridSize - 1) / gridSize;
        int rows = (maxHeight + gridSize - 1) / gridSize;
        HashMap<Integer, ArrayList<Point>> grid = new HashMap<>();

        for (int i = 0; i < numberOfPoints; ++i) {
            int attempts = 0;
            boolean valid = false;
            Point p = null;

            while (!valid && attempts < 100) {
                int x = generator.nextInt(maxWidth);
                int y = generator.nextInt(maxHeight);
                p = new Point(x, y);

                if (isValidPoint(p, grid, gridSize)) {
                    valid = true;
                } else {
                    attempts++;
                }
            }

            if (valid && p != null) {
                points.add(p);
                addToGrid(p, grid, cols, gridSize);
            } else {
                System.out.println("Failed to generate point after 100 attempts, skipping point.");
            }
        }

        return points;
    }

    private boolean isValidPoint(Point p, HashMap<Integer, ArrayList<Point>> grid, int gridSize) {
        int gridX = p.x / gridSize;
        int gridY = p.y / gridSize;
        int gridIndex = gridX + gridY * ((grid.size() + gridSize - 1) / gridSize);

        ArrayList<Point> neighbors = grid.get(gridIndex);
        if (neighbors != null) {
            for (Point neighbor : neighbors) {
                if (p.distance(neighbor) <= gridSize) {
                    return false;
                }
            }
        }

        return true;
    }

    private void addToGrid(Point p, HashMap<Integer, ArrayList<Point>> grid, int cols, int gridSize) {
        int gridX = p.x / gridSize;
        int gridY = p.y / gridSize;
        int gridIndex = gridX + gridY * cols;

        grid.putIfAbsent(gridIndex, new ArrayList<>());
        grid.get(gridIndex).add(p);
    }

    public static boolean isValide(ArrayList<Point> domSet, ArrayList<Point> points, int edgeThreshold) {
        for (Point p : points) {
            boolean isDom = false;
            for (Point q : domSet) {
                if (p.distance(q) <= edgeThreshold) {
                    isDom = true;
                    break;
                }
            }
            if (!isDom) return false;
        }
        return true;
    }

    public static double score(ArrayList<Point> inpts) {
        return inpts.size();
    }

    public static void main(String[] args) {
        int numberOfTests = 100;
        int numberOfPoints = 1000;
        int maxWidth = 1400;
        int maxHeight = 900;
        int radius = 140;
        int edgeThreshold = 55;

        DefaultTeam team = new DefaultTeam();
        for (int i = 0; i < numberOfTests; i++) {
            ArrayList<Point> points = team.generateRandomPoints(numberOfPoints, maxWidth, maxHeight, radius, edgeThreshold);
            ArrayList<Point> dominatingSet = team.calculDominatingSet(points, edgeThreshold);
            System.out.println("Test " + (i + 1) + " completed. Dominating set size: " + dominatingSet.size());
        }
    }
}

