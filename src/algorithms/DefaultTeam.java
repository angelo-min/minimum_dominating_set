package algorithms;

import java.lang.ref.Reference;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

final class Point {
    public final int id;

    Point(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Point point)) return false;
        return id == point.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}

public class DefaultTeam {
    private boolean[] edgeMap;
    private HashMap<java.awt.Point, Point> pointMap;
    private ArrayList<java.awt.Point> pointList;
    private Point[] simplePointArr;
    private int pointCount;
    private int pointCountShift;

    // optimized points set that contains a boolean array (since the amount of points is very limited)
    // has nexts and prevs array that induce a linked list that speeds up iteration for more sparse sets
    // for iteration heavy functions we try to use random access containers anyway
    private final class PointSet extends AbstractSet<Point> {
        private boolean[] points = new boolean[pointCount];
        private int[] nexts = new int[pointCount];
        private int[] prevs = new int[pointCount];
        private int first = -1;
        private int size = 0;
        private boolean cacheReset = true;
        private int hashCache = 0;

        public PointSet() {
            clear();
        }

        public PointSet(Collection<Point> collection) {
            if (collection instanceof PointSet pointSet) {
                size = pointSet.size;
                first = pointSet.first;
                System.arraycopy(pointSet.points, 0, points, 0, pointCount);
                System.arraycopy(pointSet.nexts, 0, nexts, 0, pointCount);
                System.arraycopy(pointSet.prevs, 0, prevs, 0, pointCount);
            } else {
                clear();
                addAll(collection);
            }
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public boolean contains(Object o) {
            return points[((Point) o).id];
        }

        public boolean containsId(int id) {
            return points[id];
        }

        @Override
        public Iterator<Point> iterator() {
            return new Iterator<>() {
                int prev = -1;
                int curId = first;
                @Override
                public boolean hasNext() {
                    return curId != -1;
                }

                @Override
                public Point next() {
                    Point res = simplePointArr[curId];
                    prev = curId;
                    curId = nexts[curId];
                    return res;
                }

                @Override
                public void remove() {
                    removeId(prev); // in the previous submission this was broken but we didn't use this method
                }
            };
        }

        @Override
        public boolean add(Point point) {
            int id = point.id;
            boolean changed = !points[id];
            points[id] = true;
            if (changed) {
                cacheReset = true;
                size++;
                if (first == -1) {
                    first = id;
                    return true;
                }
                if (first > id) {
                    nexts[id] = first;
                    prevs[first] = id;
                    first = id;
                    return true;
                }
                int prev = id-1;
                while (prev >= 0 && !points[prev]) {
                    prev--;
                }
                int next = nexts[prev];
                nexts[prev] = id;
                prevs[id] = prev;
                nexts[id] = next;
                if (next != -1) {
                    prevs[next] = id;
                }
            }
            return changed;
        }

        public boolean removeId(int id) {
            boolean changed = points[id];
            points[id] = false;
            if (changed) {
                cacheReset = true;
                size--;
                int prev = prevs[id], next = nexts[id];
                if (first == id) {
                    first = next;
                }
                if (prev != -1) {
                    nexts[prev] = nexts[id];
                }
                if (next != -1) {
                    prevs[next] = prevs[id];
                }
            }
            return changed;
        }

        @Override
        public boolean remove(Object o) {
            return removeId(((Point) o).id);
        }

        @Override
        public void clear() {
            Arrays.fill(points, false);
            Arrays.fill(nexts, -1);
            Arrays.fill(prevs, -1);
            first = -1;
            size = 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PointSet pointSet)) return false;
            if (size != pointSet.size) return false;
            if (!cacheReset && !pointSet.cacheReset && hashCache != pointSet.hashCache) return false;

            return Arrays.equals(points, pointSet.points);
        }

        @Override
        public int hashCode() {
            if (cacheReset) {
                hashCache = Arrays.hashCode(points);
                cacheReset = false;
            }
            return hashCache;
        }
    }

    // Main Method to Compute Dominating Set
    public ArrayList<java.awt.Point> calculDominatingSet(ArrayList<java.awt.Point> _points, int edgeThreshold) {
        // we convert the input points into simple classes that just contain an id for the sake of speed
        pointCount = _points.size();
        pointCountShift = 32 - Integer.numberOfLeadingZeros(pointCount);
        pointMap = new HashMap<>();
        pointList = new ArrayList<>();

        // use shift instead of multiplication because the isEdge function is called a lot
        edgeMap = new boolean[_points.size() << pointCountShift];
        simplePointArr = new Point[_points.size()];
        for (java.awt.Point p: _points) {
            if (pointMap.containsKey(p)) continue;
            pointList.add(p);
            Point newPoint = new Point(pointList.size() - 1);
            pointMap.put(p, newPoint);
            simplePointArr[newPoint.id] = newPoint;
        }
        PointSet points = new PointSet();
        points.addAll(pointMap.values());

        for (Point p: points) {
            for (Point q: points) {
                edgeMap[(p.id << pointCountShift) + q.id] = pointList.get(p.id).distance(pointList.get(q.id)) < edgeThreshold;
            }
        }
        ArrayList<Point> res = gamble(new ArrayList<>(points), edgeThreshold);

        return new ArrayList<>(res.stream().map(p -> pointList.get(p.id)).toList());
    }
    private boolean isEdge(Point p, Point q, int edgeThreshold) {
        return edgeMap[(p.id << pointCountShift) + q.id];
    }

    // gambling is always the answer
    private ArrayList<Point> gamble(ArrayList<Point> points, int edgeThreshold) {
        ArrayList<Point> prev = points;
        Object lock = new Object();
        while (true) {
            PointSet pointSet = new PointSet(prev);
            int threadCount = Runtime.getRuntime().availableProcessors() - 1; // generously leave one processor alone
            int itPerThread = 200;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            Semaphore semaphore = new Semaphore(0);
            var next = new Object() { // otherwise it complains about effective final
                private ArrayList<Point> next;
            };
            next.next = prev;
            for (int i = 0; i < threadCount; i++) {
                pool.execute(() -> {
                    for (int j = 0; j < itPerThread; j++) {
                        ArrayList<Point> res = calculateSet(pointSet, edgeThreshold);
                        synchronized (lock) {
                            if (res.size() < next.next.size()) {
                                next.next = res;
                            }
                        }
                        semaphore.release(1);
                    }
                });
            }
            try {
                semaphore.acquire(threadCount * itPerThread);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (next.next.size() >= prev.size()) {
                break;
            }
            prev = next.next;
            System.out.println("Current best: " + prev.size());
        }
        return prev;
    }

    private ArrayList<Point> calculateSet(PointSet points, int edgeThreshold) {
        ArrayList<Point> dominatingSet = new ArrayList<>();
        PointSet uncovered = new PointSet(points);
        Random random = new Random();

        Point[] uncoveredArr = new Point[uncovered.size]; // accelerate iteration
        {
            int i = -1;
            for (Point p: uncovered) {
                uncoveredArr[++i] = p;
            }
        }
        ArrayList<Point>[] neighbors = new ArrayList[pointCount]; // neighbor list structure
        for (Point p : uncoveredArr) {
            ArrayList<Point> neighborsP = new ArrayList<>();
            for (Point q : uncoveredArr) {
                if (isEdge(p, q, edgeThreshold)) {
                    neighborsP.add(q);
                }
            }
            neighbors[p.id] = neighborsP;
        }

        // Improved Greedy Algorithm with Weighted Selection
        while (!uncovered.isEmpty()) {
            int bestScore = Integer.MIN_VALUE;
            ArrayList<Point> bestPoints = new ArrayList<>();

            for (Point candidate : uncovered) {

                // Weighted score: prioritize high coverage and low overlap
                int score = countNewCoverage(neighbors, candidate, uncovered, edgeThreshold);
                if (score >= bestScore) {
                    if (score > bestScore) {
                        bestScore = score;
                        bestPoints.clear();
                    }
                    bestPoints.add(candidate);
                }
            }

            if (!bestPoints.isEmpty()) {
                Point bestPoint = bestPoints.get(random.nextInt(bestPoints.size()));
                dominatingSet.add(bestPoint);
                updateUncovered(bestPoint, uncovered, edgeThreshold);
            }
        }

        // Post-processing with Local Search
        //refineDominatingSet(dominatingSet, points, edgeThreshold);

        return dominatingSet;
    }

    // Count new vertices covered by adding candidate to the Dominating Set
    private int countNewCoverage(ArrayList<Point>[] neighbors, Point candidate, PointSet uncovered, int edgeThreshold) {
        int count = 0;
        for (Point p : neighbors[candidate.id]) {
            if (uncovered.containsId(p.id)) { // if we happened to remove p, ignore it
                count++;
            }
        }
        return count;
    }

    // exact same as above
    // Count overlap: how many already covered points the candidate dominates
    private int countOverlap(Point candidate, PointSet uncovered, int edgeThreshold) {
        int count = 0;
        for (Point p : uncovered) {
            if (isEdge(candidate, p, edgeThreshold)) {
                count++;
            }
        }
        return count;
    }

    // Update uncovered set after adding a vertex to the Dominating Set
    private void updateUncovered(Point dominator, Set<Point> uncovered, int edgeThreshold) {
        uncovered.removeIf(p -> isEdge(dominator, p, edgeThreshold));
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
        PointSet coverWithCandidate = new PointSet();
        
        for (Point dominator : dominatingSet) {
            Point effective = dominator.equals(toReplace) ? candidate : dominator;
            for (Point p : points) {
                if (isEdge(effective, p, edgeThreshold)) {
                    coverWithCandidate.add(p);
                }
            }
        }

        return coverWithCandidate.containsAll(points);
    }

    // Optimized Random Points Generator Logic Integration
    public ArrayList<java.awt.Point> generateRandomPoints(int numberOfPoints, int maxWidth, int maxHeight, int radius, int edgeThreshold) {
        ArrayList<java.awt.Point> points = new ArrayList<>();
        Random generator = new Random();

        int gridSize = edgeThreshold;
        int cols = (maxWidth + gridSize - 1) / gridSize;
        int rows = (maxHeight + gridSize - 1) / gridSize;
        HashMap<Integer, ArrayList<java.awt.Point>> grid = new HashMap<>();

        for (int i = 0; i < numberOfPoints; ++i) {
            int attempts = 0;
            boolean valid = false;
            java.awt.Point p = null;

            while (!valid && attempts < 100) {
                int x = generator.nextInt(maxWidth);
                int y = generator.nextInt(maxHeight);
                p = new java.awt.Point(x, y);

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

    private boolean isValidPoint(java.awt.Point p, HashMap<Integer, ArrayList<java.awt.Point>> grid, int gridSize) {
        int gridX = p.x / gridSize;
        int gridY = p.y / gridSize;
        int gridIndex = gridX + gridY * ((grid.size() + gridSize - 1) / gridSize);

        ArrayList<java.awt.Point> neighbors = grid.get(gridIndex);
        if (neighbors != null) {
            for (java.awt.Point neighbor : neighbors) {
                if (p.distance(neighbor) <= gridSize) {
                    return false;
                }
            }
        }

        return true;
    }

    private void addToGrid(java.awt.Point p, HashMap<Integer, ArrayList<java.awt.Point>> grid, int cols, int gridSize) {
        int gridX = p.x / gridSize;
        int gridY = p.y / gridSize;
        int gridIndex = gridX + gridY * cols;

        grid.putIfAbsent(gridIndex, new ArrayList<>());
        grid.get(gridIndex).add(p);
    }

    public static boolean isValide(ArrayList<java.awt.Point> domSet, ArrayList<java.awt.Point> points, int edgeThreshold) {
        for (java.awt.Point p : points) {
            boolean isDom = false;
            for (java.awt.Point q : domSet) {
                if (p.distance(q) <= edgeThreshold) {
                    isDom = true;
                    break;
                }
            }
            if (!isDom) return false;
        }
        return true;
    }

    public static double score(ArrayList<java.awt.Point> inpts) {
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
            ArrayList<java.awt.Point> points = team.generateRandomPoints(numberOfPoints, maxWidth, maxHeight, radius, edgeThreshold);
            ArrayList<java.awt.Point> dominatingSet = team.calculDominatingSet(points, edgeThreshold);
            System.out.println("Test " + (i + 1) + " completed. Dominating set size: " + dominatingSet.size());
        }
    }
}

