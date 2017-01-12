
package org.mwg.core.chunk.heap;

import org.mwg.Constants;
import org.mwg.struct.Buffer;
import org.mwg.struct.StringLongMap;
import org.mwg.struct.StringLongMapCallBack;
import org.mwg.utility.Base64;
import org.mwg.utility.HashHelper;

import java.util.Arrays;

class HeapStringLongMap implements StringLongMap {

    private final HeapContainer parent;

    private int mapSize = 0;
    private int capacity = 0;

    private String[] keys = null;
    private long[] keysH = null;
    private long[] values = null;
    private int[] nexts = null;
    private int[] hashs = null;

    HeapStringLongMap(final HeapContainer p_parent) {
        this.parent = p_parent;
    }

    private String key(int i) {
        return keys[i];
    }

    private void setKey(int i, String newValue) {
        keys[i] = newValue;
    }

    private long keyH(int i) {
        return keysH[i];
    }

    private void setKeyH(int i, long newValue) {
        keysH[i] = newValue;
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
            String[] new_keys = new String[newCapacity];
            if (keys != null) {
                System.arraycopy(keys, 0, new_keys, 0, capacity);
            }
            keys = new_keys;
            //extend keysH
            long[] new_keysH = new long[newCapacity];
            if (keysH != null) {
                System.arraycopy(keysH, 0, new_keysH, 0, capacity);
            }
            keysH = new_keysH;
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
                int new_key_hash = (int) HashHelper.longHash(keyH(i), newCapacity * 2);
                setNext(i, hash(new_key_hash));
                setHash(new_key_hash, i);
            }
            capacity = newCapacity;
        }
    }

    HeapStringLongMap cloneFor(HeapContainer newContainer) {
        HeapStringLongMap cloned = new HeapStringLongMap(newContainer);
        cloned.mapSize = mapSize;
        cloned.capacity = capacity;
        if (keys != null) {
            String[] cloned_keys = new String[capacity];
            System.arraycopy(keys, 0, cloned_keys, 0, capacity);
            cloned.keys = cloned_keys;
        }
        if (keysH != null) {
            long[] cloned_keysH = new long[capacity];
            System.arraycopy(keysH, 0, cloned_keysH, 0, capacity);
            cloned.keysH = cloned_keysH;
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
    public final long getValue(final String requestString) {
        long result = Constants.NULL_LONG;
        synchronized (parent) {
            if (keys != null) {
                final long keyHash = HashHelper.hash(requestString);
                final int hashIndex = (int) HashHelper.longHash(keyHash, capacity * 2);
                int m = hash(hashIndex);
                while (m >= 0) {
                    if (keyHash == keyH(m)) {
                        result = value(m);
                        break;
                    }
                    m = next(m);
                }
            }
        }
        return result;
    }


    @Override
    public String getByHash(final long keyHash) {
        String result = null;
        synchronized (parent) {
            if (keys != null) {
                final int hashIndex = (int) HashHelper.longHash(keyHash, capacity * 2);
                int m = hash(hashIndex);
                while (m >= 0) {
                    if (keyHash == keyH(m)) {
                        result = key(m);
                        break;
                    }
                    m = next(m);
                }
            }
        }
        return result;
    }

    @Override
    public boolean containsHash(long keyHash) {
        boolean result = false;
        synchronized (parent) {
            if (keys != null) {
                final int hashIndex = (int) HashHelper.longHash(keyHash, capacity * 2);
                int m = hash(hashIndex);
                while (m >= 0) {
                    if (keyHash == keyH(m)) {
                        result = true;
                        break;
                    }
                    m = next(m);
                }
            }
        }
        return result;
    }

    @Override
    public final void each(StringLongMapCallBack callback) {
        synchronized (parent) {
            unsafe_each(callback);
        }
    }

    void unsafe_each(StringLongMapCallBack callback) {
        for (int i = 0; i < mapSize; i++) {
            callback.on(key(i), value(i));
        }
    }

    @Override
    public long size() {
        long result;
        synchronized (parent) {
            result = mapSize;
        }
        return result;
    }

    @Override
    public final void remove(final String requestKey) {
        synchronized (parent) {
            if (keys != null && mapSize != 0) {
                final long keyHash = HashHelper.hash(requestKey);
                long hashCapacity = capacity * 2;
                int hashIndex = (int) HashHelper.longHash(keyHash, hashCapacity);
                int m = hash(hashIndex);
                int found = -1;
                while (m >= 0) {
                    if (requestKey == key(m)) {
                        found = m;
                        break;
                    }
                    m = next(m);
                }
                if (found != -1) {
                    //first remove currentKey from hashChain
                    int toRemoveHash = (int) HashHelper.longHash(keyHash, hashCapacity);
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
                        final String lastKey = key(lastIndex);
                        final long lastKeyH = keyH(lastIndex);
                        setKey(found, lastKey);
                        setKeyH(found, lastKeyH);
                        setValue(found, value(lastIndex));
                        setNext(found, next(lastIndex));
                        int victimHash = (int) HashHelper.longHash(lastKeyH, hashCapacity);
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
        }
    }

    @Override
    public final void put(final String insertKey, final long insertValue) {
        synchronized (parent) {
            final long keyHash = HashHelper.hash(insertKey);
            if (keys == null) {
                reallocate(Constants.MAP_INITIAL_CAPACITY);
                setKey(0, insertKey);
                setKeyH(0, keyHash);
                setValue(0, insertValue);
                setHash((int) HashHelper.longHash(keyHash, capacity * 2), 0);
                setNext(0, -1);
                mapSize++;
            } else {
                long hashCapacity = capacity * 2;
                int insertKeyHash = (int) HashHelper.longHash(keyHash, hashCapacity);
                int currentHash = hash(insertKeyHash);
                int m = currentHash;
                int found = -1;
                while (m >= 0) {
                    if (insertKey == key(m)) {
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
                    setKeyH(lastIndex, keyHash);
                    setValue(lastIndex, insertValue);
                    setHash((int) HashHelper.longHash(keyHash, capacity * 2), lastIndex);
                    setNext(lastIndex, currentHash);
                    mapSize++;
                    parent.declareDirty();
                } else {
                    if (value(found) != insertValue) {
                        setValue(found, insertValue);
                        parent.declareDirty();
                    }
                }
            }
        }
    }

    public final long load(final Buffer buffer, final long offset, final long max) {
        long cursor = offset;
        byte current = buffer.read(cursor);
        boolean isFirst = true;
        long previous = offset;
        String previousKey = null;
        while (cursor < max && current != Constants.CHUNK_SEP && current != Constants.CHUNK_ENODE_SEP && current != Constants.CHUNK_ESEP) {
            if (current == Constants.CHUNK_VAL_SEP) {
                if (isFirst) {
                    reallocate((int) Base64.decodeToLongWithBounds(buffer, previous, cursor));
                    isFirst = false;
                } else {
                    if (previousKey == null) {
                        previousKey = Base64.decodeToStringWithBounds(buffer, previous, cursor);
                    } else {
                        put(previousKey, Base64.decodeToLongWithBounds(buffer, previous, cursor));
                        previousKey = null;
                    }
                }
                previous = cursor + 1;
            }
            cursor++;
            if (cursor < max) {
                current = buffer.read(cursor);
            }
        }
        if (isFirst) {
            reallocate((int) Base64.decodeToLongWithBounds(buffer, previous, cursor));
        } else {
            if (previousKey != null) {
                put(previousKey, Base64.decodeToLongWithBounds(buffer, previous, cursor));
            }
        }
        return cursor;
    }

}



