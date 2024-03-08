package com.akto.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dto.type.KeyTypes;

public class Trie {
    private static final Logger logger = LoggerFactory.getLogger(Trie.class);

    public static class Node<K, V> {
        public static<K, V> Node<K, V> createTerminal(K pathElem, V value) {
            return new Node<K, V>(pathElem, value, new HashMap<>());
        }

        K pathElem;
        V value;
        Map<Node<K, V>, Node<K, V>> children;

        private Node(K pathElem, V value, Map<Node<K, V>, Node<K, V>> children) {
            this.pathElem = pathElem;
            this.value = value;
            this.children = children;
        }

        public Node<K, V> getOrCreate(K name, V defaultValue) {
            Node<K, V> key = Node.createTerminal(name, defaultValue);
            Node<K, V> ret = children.get(key);

            if (ret == null) {
                ret = key;
                children.put(key, key);
            }

            return ret;
        }

        public Node<K, V> get(K name) {
            Node<K, V> key = Node.createTerminal(name, null);
            return children.get(key);
        }

        public void mergeFrom(Node<K, V> that, BiConsumer<V, V> mergeKeyFunc) {
            if (that.children != null) {
                for(Node<K, V> thatChild: that.children.keySet()) {
                    Node<K, V> thisChild = this.children.get(thatChild.getPathElem());
                    if (thisChild == null) {
                        this.children.put(thatChild, thatChild);
                    } else {
                        thisChild.mergeFrom(thatChild, mergeKeyFunc);
                    }
                }
            }

            mergeKeyFunc.accept(this.getValue(), that.getValue());
        }

        public K getPathElem() {
            return this.pathElem;
        }

        public V getValue() {
            return this.value;
        }

        public Map<Node<K, V>, Node<K, V>> getChildren() {
            return this.children;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof Node)) {
                return false;
            }
            Node<K, V> node = (Node<K, V>) o;
            return pathElem.equals(node.getPathElem());
        }
    
        @Override
        public int hashCode() {
            return pathElem.hashCode();
        } 
        
        public void print(Consumer<Node<K, V>> consumer) {
            printHelper(0, consumer);
        }

        void printHelper(int tab, Consumer<Node<K, V>> consumer) {
            for(int i = 0; i < tab; i++) {
                logger.info("\t");
            }

            if (consumer != null) consumer.accept(this);
            for(Node<K, V> node: children.values()) {
                node.printHelper(tab+1, consumer);
            }
        }
    }
    
    Node<String, Pair<KeyTypes, Set<String>>> root;

    public Trie() {
        root = Node.createTerminal("", new Pair<KeyTypes, Set<String>>(new KeyTypes(new HashMap<>(), false), new HashSet<String>()));
    }

    public Node<String, Pair<KeyTypes, Set<String>>> getRoot() {
        return this.root;
    }

    public void flatten(Map<String, KeyTypes> parameters) {

        for (Node<String, Pair<KeyTypes, Set<String>>> node: root.children.keySet()) {
            flattenHelper(node, parameters, "");    
        }
    }

    private void flattenHelper(Node<String, Pair<KeyTypes, Set<String>>> node, Map<String, KeyTypes> parameters, String prefix) {
        prefix += ("#"+node.pathElem);

        if (prefix.startsWith("#")) {
            prefix = prefix.substring(1);
        }

        KeyTypes keyTypes = node.value.getFirst();
        if (keyTypes != null && !keyTypes.getOccurrences().isEmpty()) {
            KeyTypes curr = parameters.get(prefix);
            if (curr == null) {
                parameters.put(prefix, keyTypes.copy());
            } else {
                curr.getOccurrences().putAll(keyTypes.getOccurrences());
            }
        }

        if (node.children != null && !node.children.isEmpty()) {
            for(Node<String, Pair<KeyTypes, Set<String>>> child: node.getChildren().keySet()) {
                flattenHelper(child, parameters, prefix);
            }
        }
    }
}