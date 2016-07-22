package com.akalenda.MoogleKiwi.Permutator;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Note that the Google Guava library already has a variety of permutations algorithms available in
 * {@link com.google.common.collect.Collections2}. If you want the entire list of permutations, you should probably use
 * those instead. This class here is specifically for giving permutations through a Generator, e.g., permutations are
 * only generated one-by-one as requested through {@link #next()}.
 *
 * This generator's algorithm operates by enumerating each value in the list to give a lexicographic ordering, and
 * iterating through the permutations simply becomes an exercise in incrementing through that ordering.
 */
class Permutator<E> implements Iterator<List<E>> {

    private final ImmutableBiMap<Integer, E> lexicography;
    private List<Integer> nextPermutation;
    private List<E> currentOutput;
    private Optional<NoSuchElementException> noMorePerms = Optional.empty();

    Permutator(List<E> toBePermuted) {
        final AtomicInteger counter = new AtomicInteger(0);
        lexicography = ImmutableBiMap.copyOf(toBePermuted.stream().distinct().collect(Collectors.toMap(
                ignored -> counter.getAndIncrement(),
                Function.identity()
        )));
        nextPermutation = toBePermuted.stream().map(lexicography.inverse()::get).collect(Collectors.toList());
        Collections.sort(nextPermutation);
        currentOutput = new ArrayList<>(Collections.nCopies(toBePermuted.size(), toBePermuted.get(0)));
    }

    /* ******************************* Core functions **********************************************************/

    @Override
    public List<E> next() {
        if (noMorePerms.isPresent()) {
            NoSuchElementException e = new NoSuchElementException("There are no more permutations to be had.\n"
                    + "The suppressed exception shows at what point the last permutation was found.");
            e.addSuppressed(noMorePerms.get());
            throw e;
        }
        updateCurrentPermutation();
        try {
            prepareNextPermutation();
        } catch (NoSuchElementException e) {
            noMorePerms = Optional.of(e);
        }
        return currentOutput;
    }

    private void updateCurrentPermutation() {
        currentOutput = nextPermutation.stream().map(lexicography::get).collect(Collectors.toList());
    }

    private void prepareNextPermutation() {
        int firstUnfixedElement = indexOfLastAscendingValue() - 1;
        int migratingValue = indexOfLastElementLessThan(nextPermutation.get(firstUnfixedElement));
        swapElementsAtIndices(firstUnfixedElement, migratingValue);
        reverseElementsInRange(firstUnfixedElement + 1, nextPermutation.size() - 1);
    }

    /* ******************************* Helpers **********************************************************************/

    private int indexOfLastAscendingValue() {
        for (int right = nextPermutation.size() - 1; right > 0; right--)
            if (nextPermutation.get(right - 1) < nextPermutation.get(right))
                return right;
        throw new NoSuchElementException("We have already found all permutations, there is no reason to continue.");
    }

    private int indexOfLastElementLessThan(int x) {
        int i = nextPermutation.size() - 1;
        while (nextPermutation.get(i) <= x)
            i--;
        return i;
    }

    private void reverseElementsInRange(int leftIndex, int rightIndex) {
        while (leftIndex < rightIndex) {
            swapElementsAtIndices(leftIndex, rightIndex);
            leftIndex++;
            rightIndex--;
        }
    }

    private void swapElementsAtIndices(int i, int j) {
        int x = nextPermutation.get(i);
        int y = nextPermutation.get(j);
        nextPermutation.set(i, y);
        nextPermutation.set(j, x);
    }

    public static void main(String[] args) {
        ImmutableList<Character> target = ImmutableList.of('a', 'b', 'c', 'a', 'e', 'f');
        Permutator<Character> perm = new Permutator<>(target);
        int totalFound = 0;
        while (perm.hasNext()) {
            totalFound++;
            System.out.println(perm.next());
        }
        System.out.println("Total found: " + totalFound);
    }

    /* ********************************* Iterator implementations ************************************************/

    @Override
    public boolean hasNext() {
        return !noMorePerms.isPresent();
    }

    @Override
    public void remove() {
        // TODO: Usage of this function should cause the permutation that the user has just seen to be omitted from collection. Which is only relevant when this is used as a stream.
    }
}