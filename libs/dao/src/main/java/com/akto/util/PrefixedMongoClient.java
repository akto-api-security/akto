package com.akto.util;

import com.mongodb.ClientSessionOptions;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.ClientSession;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.connection.ClusterDescription;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.List;

/**
 * Translates every database name the code asks for into its physical name (see DbNames), so
 * DAOs keep using "common", "billing" and bare account ids. Everything else is delegated as is.
 *
 * listDatabaseNames()/listDatabases() return physical names; callers enumerating databases map
 * them back with DbNames.logical(), which also filters out other deployments' databases.
 */
class PrefixedMongoClient implements MongoClient {
    private final MongoClient delegate;

    PrefixedMongoClient(MongoClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public MongoDatabase getDatabase(String databaseName) {
        return delegate.getDatabase(DbNames.physical(databaseName));
    }

    @Override public ClientSession startSession() { return delegate.startSession(); }
    @Override public ClientSession startSession(ClientSessionOptions options) { return delegate.startSession(options); }
    @Override public void close() { delegate.close(); }
    @Override public MongoIterable<String> listDatabaseNames() { return delegate.listDatabaseNames(); }
    @Override public MongoIterable<String> listDatabaseNames(ClientSession s) { return delegate.listDatabaseNames(s); }
    @Override public ListDatabasesIterable<Document> listDatabases() { return delegate.listDatabases(); }
    @Override public ListDatabasesIterable<Document> listDatabases(ClientSession s) { return delegate.listDatabases(s); }
    @Override public <T> ListDatabasesIterable<T> listDatabases(Class<T> c) { return delegate.listDatabases(c); }
    @Override public <T> ListDatabasesIterable<T> listDatabases(ClientSession s, Class<T> c) { return delegate.listDatabases(s, c); }
    @Override public ChangeStreamIterable<Document> watch() { return delegate.watch(); }
    @Override public <T> ChangeStreamIterable<T> watch(Class<T> c) { return delegate.watch(c); }
    @Override public ChangeStreamIterable<Document> watch(List<? extends Bson> p) { return delegate.watch(p); }
    @Override public <T> ChangeStreamIterable<T> watch(List<? extends Bson> p, Class<T> c) { return delegate.watch(p, c); }
    @Override public ChangeStreamIterable<Document> watch(ClientSession s) { return delegate.watch(s); }
    @Override public <T> ChangeStreamIterable<T> watch(ClientSession s, Class<T> c) { return delegate.watch(s, c); }
    @Override public ChangeStreamIterable<Document> watch(ClientSession s, List<? extends Bson> p) { return delegate.watch(s, p); }
    @Override public <T> ChangeStreamIterable<T> watch(ClientSession s, List<? extends Bson> p, Class<T> c) { return delegate.watch(s, p, c); }
    @Override public ClusterDescription getClusterDescription() { return delegate.getClusterDescription(); }
}
