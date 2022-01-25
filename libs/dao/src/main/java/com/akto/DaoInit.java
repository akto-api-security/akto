package com.akto;

import com.akto.dto.*;
import com.akto.dto.notifications.content.Content;
import com.akto.dto.third_party_access.Credential;
import com.akto.dto.third_party_access.ThirdPartyAccess;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.util.EnumCodec;
import com.akto.dto.Attempt.AttemptResult;
import com.akto.dto.auth.APIAuth;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;

import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.PojoCodecProvider;

import static com.akto.dao.MCollection.clients;
import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class DaoInit {

    public static void init(ConnectionString connectionString) {
        ClassModel<MessageQueueEntry> queueEntryClassModel = ClassModel.builder(MessageQueueEntry.class).enableDiscriminator(true).build();
        ClassModel<Config> configClassModel = ClassModel.builder(Config.class).enableDiscriminator(true).build();
        ClassModel<SignupInfo> signupInfoClassModel = ClassModel.builder(SignupInfo.class).enableDiscriminator(true).build();
        ClassModel<Content> contentClassModel = ClassModel.builder(Content.class).enableDiscriminator(true).build();
        ClassModel<APIAuth> apiAuthClassModel = ClassModel.builder(APIAuth.class).enableDiscriminator(true).build();
        ClassModel<AttemptResult> attempResultModel = ClassModel.builder(AttemptResult.class).enableDiscriminator(true).build();
        ClassModel<URLTemplate> urlTemplateModel = ClassModel.builder(URLTemplate.class).enableDiscriminator(true).build();
        ClassModel<PendingInviteCode> pendingInviteCodeClassModel = ClassModel.builder(PendingInviteCode.class).enableDiscriminator(true).build();
        ClassModel<RBAC> rbacClassModel = ClassModel.builder(RBAC.class).enableDiscriminator(true).build();
        ClassModel<SingleTypeInfo> singleTypeInfoClassModel = ClassModel.builder(SingleTypeInfo.class).enableDiscriminator(true).build();
        ClassModel<KafkaHealthMetric>  kafkaHealthMetricClassModel = ClassModel.builder(KafkaHealthMetric.class).enableDiscriminator(true).build();
        ClassModel<ThirdPartyAccess>  thirdPartyAccessClassModel = ClassModel.builder(ThirdPartyAccess.class).enableDiscriminator(true).build();
        ClassModel<Credential>   credentialClassModel = ClassModel.builder(Credential.class).enableDiscriminator(true).build();
        ClassModel<ApiToken> apiTokenClassModel = ClassModel.builder(ApiToken.class).enableDiscriminator(true).build();

        CodecRegistry pojoCodecRegistry = fromProviders(PojoCodecProvider.builder().register(queueEntryClassModel, configClassModel, 
            signupInfoClassModel, contentClassModel, apiAuthClassModel, attempResultModel, urlTemplateModel,
                pendingInviteCodeClassModel, rbacClassModel, kafkaHealthMetricClassModel,singleTypeInfoClassModel,
                thirdPartyAccessClassModel, credentialClassModel, apiTokenClassModel).automatic(true).build());

        final CodecRegistry customEnumCodecs = CodecRegistries.fromCodecs(
            new EnumCodec<>(SingleTypeInfo.SubType.class),
            new EnumCodec<>(SingleTypeInfo.SuperType.class),
            new EnumCodec<>(URLMethods.Method.class),
            new EnumCodec<>(RBAC.Role.class),
            new EnumCodec<>(Credential.Type.class),
            new EnumCodec<>(ApiToken.Utility.class)
        );

        CodecRegistry codecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), pojoCodecRegistry, customEnumCodecs);
        MongoClientSettings clientSettings = MongoClientSettings.builder()
            .applyConnectionString(connectionString)
            .codecRegistry(codecRegistry)
            .build();

        clients[0] = MongoClients.create(clientSettings);
    }

}
