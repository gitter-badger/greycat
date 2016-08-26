
package org.mwg.core.chunk.heap;

import org.mwg.Constants;
import org.mwg.chunk.ChunkListener;
import org.mwg.utility.HashHelper;
import org.mwg.struct.LongLongArrayMap;
import org.mwg.struct.LongLongArrayMapCallBack;

import java.util.Arrays;

class HeapLongLongArrayMap implements LongLongArrayMap {

    private final ChunkListener parent;

    private int mapSize = 0;
    private int capacity = 0;

    private long[] keys = null;
    private long[] values = null;

    private int[] nexts = null;
    private int[] hashs = null;

    HeapLongLongArrayMap(final ChunkListener p_listener) {
        this.parent = p_listener;
    }

    private long key(int i) {
        return keys[i];
    }

    private void setKey(int i, long newValue) {
        keys[i] = newValue;
    }

    private long value(int i) {
        return values[i];
    }

    private void setValue(int i, long newValue) {
        values[i] = newValue;
    }

    private int next(int i) {
        return nexts[i];
    }

    private void setNext(int i, int newValue) {
        nexts[i] = newValue;
    }

    private int hash(int i) {
        return hashs[i];
    }

    private void setHash(int i, int newValue) {
        hashs[i] = newValue;
    }

    void reallocate(int newCapacity) {
        if (newCapacity > capacity) {
            //extend keys
            long[] new_keys = new long[newCapacity];
            if (keys != null) {
                System.arraycopy(keys, 0, new_keys, 0, capacity);
            }
            keys = new_keys;
            //extend values
            long[] new_values = new long[newCapacity];
            if (values != null) {
                System.arraycopy(values, 0, new_values, 0, capacity);
            }
            values = new_values;

            int[] new_nexts = new int[newCapacity];
            int[] new_hashes = new int[newCapacity * 2];
            Arrays.fill(new_nexts, 0, newCapacity, -1);
            Arrays.fill(new_hashes, 0, newCapacity * 2, -1);
            hashs = new_hashes;
            nexts = new_nexts;
            for (int i = 0; i < mapSize; i++) {
                int new_key_hash = (int) HashHelper.longHash(key(i), newCapacity * 2);
                setNext(i, hash(new_key_hash));
                setHash(new_key_hash, i);
            }
            capacity = newCapacity;
        }
    }

    HeapLongLongArrayMap cloneFor(HeapStateChunk newParent) {
        HeapLongLongArrayMap cloned = new HeapLongLongArrayMap(newParent);
        cloned.mapSize = mapSize;
        cloned.capacity = capacity;
        if (keys != null) {
            long[] cloned_keys = new long[capacity];
            System.arraycopy(keys, 0, cloned_keys, 0, capacity);
            cloned.keys = cloned_keys;
        }
        if (values != null) {
            long[] cloned_values = new long[capacity];
            System.arraycopy(values, 0, cloned_values, 0, capacity);
            cloned.values = cloned_values;
        }
        if (nexts != null) {
            int[] cloned_nexts = new int[capacity];
            System.arraycopy(nexts, 0, cloned_nexts, 0, capacity);
            cloned.nexts = cloned_nexts;
        }
        if (hashs != null) {
            int[] cloned_hashs = new int[capacity * 2];
            System.arraycopy(hashs, 0, cloned_hashs, 0, capacity * 2);
            cloned.hashs = cloned_hashs;
        }
        return cloned;
    }

    @Override
    public synchronized final long[] get(final long requestKey) {
        if (keys == null) {
            return new long[0];
        }
        final int hashIndex = (int) HashHelper.longHash(requestKey, capacity * 2);
        long[] result = new long[0];
        int resultCapacity = 0;
        int resultIndex = 0;
        int m = hash(hashIndex);
        while (m >= 0) {
            if (requestKey == key(m)) {
                if (resultIndex == resultCapacity) {
                    int newCapacity;
                    if (resultCapacity == 0) {
                        newCapacity = 1;
                    } else {
                        newCapacity = resultCapacity * 2;
                    }
                    long[] tempResult = new long[newCapacity];
                    System.arraycopy(result, 0, tempResult, 0, result.length);
                    result = tempResult;
                    resultCapacity = newCapacity;
                }
                result[resultIndex] = value(m);
                resultIndex++;
            }
            m = next(m);
        }
        if (resultIndex == resultCapacity) {
            return result;
        } else {
            //shrink result
            long[] shrinkedResult = new long[resultIndex];
            System.arraycopy(result, 0, shrinkedResult, 0, resultIndex);
            return shrinkedResult;
        }
    }

    @Override
    public synchronized final void each(LongLongArrayMapCallBack callback) {
        for (int i = 0; i < mapSize; i++) {
            callback.on(key(i), value(i));
        }
    }

    @Override
    public synchronized long size() {
        return mapSize;
    }

    @Override
    public synchronized final void remove(final long requestKey, final long requestValue) {
        if (keys == null || mapSize == 0) {
            return;
        }
        long hashCapacity = capacity * 2;
        int hashIndex = (int) HashHelper.longHash(requestKey, hashCapacity);
        int m = hash(hashIndex);
        int found = -1;
        while (m >= 0) {
            if (requestKey == key(m) && requestValue == value(m)) {
                found = m;
                break;
            }
            m = next(m);
        }
        if (found != -1) {
            //first remove currentKey from hashChain
            int toRemoveHash = (int) HashHelper.longHash(requestKey, hashCapacity);
            m = hash(toRemoveHash);
            if (m == found) {
                setHash(toRemoveHash, next(m));
            } else {
                while (m != -1) {
                    int next_of_m = next(m);
                    if (next_of_m == found) {
                        setNext(m, next(next_of_m));
                        break;
                    }
                    m = next_of_m;
                }
            }
            final int lastIndex = mapSize - 1;
            if (lastIndex == found) {
                //easy, was the last element
                mapSize--;
            } else {
                //less cool, we have to unchain the last value of the map
                final long lastKey = key(lastIndex);
                setKey(found, lastKey);
                setValue(found, value(lastIndex));
                setNext(found, next(lastIndex));
                int victimHash = (int) HashHelper.longHash(lastKey, hashCapacity);
                m = hash(victimHash);
                if (m == lastIndex) {
                    //the victim was the head of hashing list
                    setHash(victimHash, found);
                } else {
                    //the victim is in the next, reChain it
                    while (m != -1) {
                        int next_of_m = next(m);
                        if (next_of_m == lastIndex) {
                            setNext(m, found);
                            break;
                        }
                        m = next_of_m;
                    }
                }
                mapSize--;
            }
            parent.declareDirty();
        }
    }

    @Override
    public synchronized final void put(final long insertKey, final long insertValue) {
        if (keys == null) {
            reallocate(Constants.MAP_INITIAL_CAPACITY);
            setKey(0, insertKey);
            setValue(0, insertValue);
            setHash((int) HashHelper.longHash(insertKey, capacity * 2), 0);
            setNext(0, -1);
            mapSize++;
            parent.declareDirty();
        } else {
            long hashCapacity = capacity * 2;
            int insertKeyHash = (int) HashHelper.longHash(insertKey, hashCapacity);
            int currentHash = hash(insertKeyHash);
            int m = currentHash;
            int found = -1;
            while (m >= 0) {
                if (insertKey == key(m) && insertValue == value(m)) {
                    found = m;
                    break;
                }
                m = next(m);
            }
            if (found == -1) {
                final int lastIndex = mapSize;
                if (lastIndex == capacity) {
                    reallocate(capacity * 2);
                }
                setKey(lastIndex, insertKey);
                setValue(lastIndex, insertValue);
                setHash((int) HashHelper.longHash(insertKey, capacity * 2), lastIndex);
                setNext(lastIndex, currentHash);
                mapSize++;
                parent.declareDirty();
            }
        }

    }

}



