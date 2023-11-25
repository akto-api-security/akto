package com.akto.stigg;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

public class Stigg {


    public enum BillingAnchor {
        SUBSCRIPTION_START,
        START_OF_THE_MONTH

    }

    public enum ProrationBehavior {
        INVOICE_IMMEDIATELY,
        CREATE_PRORATIONS

    }

    public enum AccountStatus {
        BLOCKED,
        ACTIVE

    }



    public enum ApiKeyType {
        SERVER,
        CLIENT

    }


    public static class EnvironmentApiKeysArgs {
        private ApiKeyFilterInput filter;
        private Iterable<ApiKeySortInput> sorting;

        public EnvironmentApiKeysArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new ApiKeyFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<ApiKeySortInput>) args.get("sorting");
                }
            }
        }

        public ApiKeyFilterInput getFilter() { return this.filter; }
        public Iterable<ApiKeySortInput> getSorting() { return this.sorting; }
        public void setFilter(ApiKeyFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<ApiKeySortInput> sorting) { this.sorting = sorting; }
    }
    /** EnvironmentProvisionStatus. */
    public enum EnvironmentProvisionStatus {
        NOT_PROVISIONED,
        IN_PROGRESS,
        FAILED,
        DONE

    }

    public static class ApiKeyFilterInput {
        private Iterable<ApiKeyFilterInput> and;
        private Iterable<ApiKeyFilterInput> or;
        private StringFieldComparisonInput id;

        public ApiKeyFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<ApiKeyFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<ApiKeyFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
            }
        }

        public Iterable<ApiKeyFilterInput> getAnd() { return this.and; }
        public Iterable<ApiKeyFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public void setAnd(Iterable<ApiKeyFilterInput> and) { this.and = and; }
        public void setOr(Iterable<ApiKeyFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
    }
    public static class StringFieldComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private String eq;
        private String neq;
        private String gt;
        private String gte;
        private String lt;
        private String lte;
        private String like;
        private String notLike;
        private String iLike;
        private String notILike;
        private Iterable<String> in;
        private Iterable<String> notIn;

        public StringFieldComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                this.eq = (String) args.get("eq");
                this.neq = (String) args.get("neq");
                this.gt = (String) args.get("gt");
                this.gte = (String) args.get("gte");
                this.lt = (String) args.get("lt");
                this.lte = (String) args.get("lte");
                this.like = (String) args.get("like");
                this.notLike = (String) args.get("notLike");
                this.iLike = (String) args.get("iLike");
                this.notILike = (String) args.get("notILike");
                this.in = (Iterable<String>) args.get("in");
                this.notIn = (Iterable<String>) args.get("notIn");
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public String getEq() { return this.eq; }
        public String getNeq() { return this.neq; }
        public String getGt() { return this.gt; }
        public String getGte() { return this.gte; }
        public String getLt() { return this.lt; }
        public String getLte() { return this.lte; }
        public String getLike() { return this.like; }
        public String getNotLike() { return this.notLike; }
        public String getILike() { return this.iLike; }
        public String getNotILike() { return this.notILike; }
        public Iterable<String> getIn() { return this.in; }
        public Iterable<String> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(String eq) { this.eq = eq; }
        public void setNeq(String neq) { this.neq = neq; }
        public void setGt(String gt) { this.gt = gt; }
        public void setGte(String gte) { this.gte = gte; }
        public void setLt(String lt) { this.lt = lt; }
        public void setLte(String lte) { this.lte = lte; }
        public void setLike(String like) { this.like = like; }
        public void setNotLike(String notLike) { this.notLike = notLike; }
        public void setILike(String iLike) { this.iLike = iLike; }
        public void setNotILike(String notILike) { this.notILike = notILike; }
        public void setIn(Iterable<String> in) { this.in = in; }
        public void setNotIn(Iterable<String> notIn) { this.notIn = notIn; }
    }
    public static class ApiKeySortInput {
        private ApiKeySortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public ApiKeySortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof ApiKeySortFields) {
                    this.field = (ApiKeySortFields) args.get("field");
                } else {
                    this.field = ApiKeySortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public ApiKeySortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(ApiKeySortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum ApiKeySortFields {
        id

    }

    /** Sort Directions */
    public enum SortDirection {
        ASC,
        DESC

    }

    /** Sort Nulls Options */
    public enum SortNulls {
        NULLS_FIRST,
        NULLS_LAST

    }


    /** The type of the feature */
    public enum FeatureType {
        BOOLEAN,
        NUMBER

    }

    /** The meter type of the feature */
    public enum MeterType {
        None,
        Fluctuating,
        Incremental

    }

    /** Feature status. */
    public enum FeatureStatus {
        NEW,
        SUSPENDED,
        ACTIVE

    }


    /** Aggregation function */
    public enum AggregationFunction {
        SUM,
        MAX,
        MIN,
        AVG,
        COUNT,
        UNIQUE

    }


    /** Condition operation */
    public enum ConditionOperation {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        IS_NULL,
        IS_NOT_NULL

    }




    /** Entitlement reset period. */
    public enum EntitlementResetPeriod {
        MONTH,
        WEEK,
        DAY,
        HOUR

    }


    /** Montly reset period according to configuration */
    public enum MonthlyAccordingTo {
        SubscriptionStart,
        StartOfTheMonth

    }


    /** Weekly reset period according to configuration */
    public enum WeeklyAccordingTo {
        SubscriptionStart,
        EverySunday,
        EveryMonday,
        EveryTuesday,
        EveryWednesday,
        EveryThursday,
        EveryFriday,
        EverySaturday

    }

    /** The widget type */
    public enum WidgetType {
        PAYWALL,
        CUSTOMER_PORTAL,
        CHECKOUT

    }


    /** Currency */
    public enum Currency {
        USD,
        AED,
        ALL,
        AMD,
        ANG,
        AUD,
        AWG,
        AZN,
        BAM,
        BBD,
        BDT,
        BGN,
        BIF,
        BMD,
        BND,
        BSD,
        BWP,
        BYN,
        BZD,
        CAD,
        CDF,
        CHF,
        CNY,
        CZK,
        DKK,
        DOP,
        DZD,
        EGP,
        ETB,
        EUR,
        FJD,
        GBP,
        GEL,
        GIP,
        GMD,
        GYD,
        HKD,
        HRK,
        HTG,
        IDR,
        ILS,
        INR,
        ISK,
        JMD,
        JPY,
        KES,
        KGS,
        KHR,
        KMF,
        KRW,
        KYD,
        KZT,
        LBP,
        LKR,
        LRD,
        LSL,
        MAD,
        MDL,
        MGA,
        MKD,
        MMK,
        MNT,
        MOP,
        MRO,
        MVR,
        MWK,
        MXN,
        MYR,
        MZN,
        NAD,
        NGN,
        NOK,
        NPR,
        NZD,
        PGK,
        PHP,
        PKR,
        PLN,
        QAR,
        RON,
        RSD,
        RUB,
        RWF,
        SAR,
        SBD,
        SCR,
        SEK,
        SGD,
        SLE,
        SLL,
        SOS,
        SZL,
        THB,
        TJS,
        TOP,
        TRY,
        TTD,
        TZS,
        UAH,
        UZS,
        VND,
        VUV,
        WST,
        XAF,
        XCD,
        YER,
        ZAR,
        ZMW,
        CLP,
        DJF,
        GNF,
        UGX,
        PYG,
        XOF,
        XPF

    }



    /** Billing period. */
    public enum BillingPeriod {
        MONTHLY,
        ANNUALLY

    }

    /** Billing model. */
    public enum BillingModel {
        FLAT_FEE,
        PER_UNIT,
        USAGE_BASED

    }

    /** Tiers mode. */
    public enum TiersMode {
        VOLUME,
        GRADUATED

    }


    /** day or month. */
    public enum TrialPeriodUnits {
        DAY,
        MONTH

    }


    public enum ChangeType {
        REORDERED,
        MODIFIED,
        ADDED,
        DELETED

    }








    /** packageName pricing type. */
    public enum PricingType {
        FREE,
        PAID,
        CUSTOM

    }






    /** Status of the integration sync */
    public enum SyncStatus {
        PENDING,
        ERROR,
        SUCCESS,
        NO_SYNC_REQUIRED

    }


    /** packageName status. */
    public enum PackageStatus {
        DRAFT,
        PUBLISHED,
        ARCHIVED

    }


    /**  */
    public enum SubscriptionEndSetup {
        DOWNGRADE_TO_FREE,
        CANCEL_SUBSCRIPTION

    }

    /**  */
    public enum SubscriptionCancellationTime {
        END_OF_BILLING_PERIOD,
        IMMEDIATE,
        SPECIFIC_DATE

    }

    /**  */
    public enum SubscriptionStartSetup {
        PLAN_SELECTION,
        TRIAL_PERIOD,
        FREE_PLAN

    }



    public static class AddonPricesArgs {
        private PriceFilterInput filter;
        private Iterable<PriceSortInput> sorting;

        public AddonPricesArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new PriceFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PriceSortInput>) args.get("sorting");
                }
            }
        }

        public PriceFilterInput getFilter() { return this.filter; }
        public Iterable<PriceSortInput> getSorting() { return this.sorting; }
        public void setFilter(PriceFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PriceSortInput> sorting) { this.sorting = sorting; }
    }
    public static class PriceFilterInput {
        private Iterable<PriceFilterInput> and;
        private Iterable<PriceFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private BillingPeriodFilterComparisonInput billingPeriod;
        private BillingModelFilterComparisonInput billingModel;
        private TiersModeFilterComparisonInput tiersMode;
        private StringFieldComparisonInput billingId;
        private PriceFilterPackageDtoFilterInput packageName;

        public PriceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PriceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PriceFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.billingPeriod = new BillingPeriodFilterComparisonInput((Map<String, Object>) args.get("billingPeriod"));
                this.billingModel = new BillingModelFilterComparisonInput((Map<String, Object>) args.get("billingModel"));
                this.tiersMode = new TiersModeFilterComparisonInput((Map<String, Object>) args.get("tiersMode"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.packageName = new PriceFilterPackageDtoFilterInput((Map<String, Object>) args.get("packageName"));
            }
        }

        public Iterable<PriceFilterInput> getAnd() { return this.and; }
        public Iterable<PriceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public BillingPeriodFilterComparisonInput getBillingPeriod() { return this.billingPeriod; }
        public BillingModelFilterComparisonInput getBillingModel() { return this.billingModel; }
        public TiersModeFilterComparisonInput getTiersMode() { return this.tiersMode; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public PriceFilterPackageDtoFilterInput getPackage() { return this.packageName; }
        public void setAnd(Iterable<PriceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PriceFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setBillingPeriod(BillingPeriodFilterComparisonInput billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingModel(BillingModelFilterComparisonInput billingModel) { this.billingModel = billingModel; }
        public void setTiersMode(TiersModeFilterComparisonInput tiersMode) { this.tiersMode = tiersMode; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setPackage(PriceFilterPackageDtoFilterInput packageName) { this.packageName = packageName; }
    }
    public static class DateFieldComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private Object eq;
        private Object neq;
        private Object gt;
        private Object gte;
        private Object lt;
        private Object lte;
        private Iterable<Object> in;
        private Iterable<Object> notIn;
        private DateFieldComparisonBetweenInput between;
        private DateFieldComparisonBetweenInput notBetween;

        public DateFieldComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                this.eq = (Object) args.get("eq");
                this.neq = (Object) args.get("neq");
                this.gt = (Object) args.get("gt");
                this.gte = (Object) args.get("gte");
                this.lt = (Object) args.get("lt");
                this.lte = (Object) args.get("lte");
                this.in = (Iterable<Object>) args.get("in");
                this.notIn = (Iterable<Object>) args.get("notIn");
                this.between = new DateFieldComparisonBetweenInput((Map<String, Object>) args.get("between"));
                this.notBetween = new DateFieldComparisonBetweenInput((Map<String, Object>) args.get("notBetween"));
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public Object getEq() { return this.eq; }
        public Object getNeq() { return this.neq; }
        public Object getGt() { return this.gt; }
        public Object getGte() { return this.gte; }
        public Object getLt() { return this.lt; }
        public Object getLte() { return this.lte; }
        public Iterable<Object> getIn() { return this.in; }
        public Iterable<Object> getNotIn() { return this.notIn; }
        public DateFieldComparisonBetweenInput getBetween() { return this.between; }
        public DateFieldComparisonBetweenInput getNotBetween() { return this.notBetween; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(Object eq) { this.eq = eq; }
        public void setNeq(Object neq) { this.neq = neq; }
        public void setGt(Object gt) { this.gt = gt; }
        public void setGte(Object gte) { this.gte = gte; }
        public void setLt(Object lt) { this.lt = lt; }
        public void setLte(Object lte) { this.lte = lte; }
        public void setIn(Iterable<Object> in) { this.in = in; }
        public void setNotIn(Iterable<Object> notIn) { this.notIn = notIn; }
        public void setBetween(DateFieldComparisonBetweenInput between) { this.between = between; }
        public void setNotBetween(DateFieldComparisonBetweenInput notBetween) { this.notBetween = notBetween; }
    }
    public static class DateFieldComparisonBetweenInput {
        private Object lower;
        private Object upper;

        public DateFieldComparisonBetweenInput(Map<String, Object> args) {
            if (args != null) {
                this.lower = (Object) args.get("lower");
                this.upper = (Object) args.get("upper");
            }
        }

        public Object getLower() { return this.lower; }
        public Object getUpper() { return this.upper; }
        public void setLower(Object lower) { this.lower = lower; }
        public void setUpper(Object upper) { this.upper = upper; }
    }
    public static class BillingPeriodFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private BillingPeriod eq;
        private BillingPeriod neq;
        private BillingPeriod gt;
        private BillingPeriod gte;
        private BillingPeriod lt;
        private BillingPeriod lte;
        private BillingPeriod like;
        private BillingPeriod notLike;
        private BillingPeriod iLike;
        private BillingPeriod notILike;
        private Iterable<BillingPeriod> in;
        private Iterable<BillingPeriod> notIn;

        public BillingPeriodFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof BillingPeriod) {
                    this.eq = (BillingPeriod) args.get("eq");
                } else {
                    this.eq = BillingPeriod.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof BillingPeriod) {
                    this.neq = (BillingPeriod) args.get("neq");
                } else {
                    this.neq = BillingPeriod.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof BillingPeriod) {
                    this.gt = (BillingPeriod) args.get("gt");
                } else {
                    this.gt = BillingPeriod.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof BillingPeriod) {
                    this.gte = (BillingPeriod) args.get("gte");
                } else {
                    this.gte = BillingPeriod.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof BillingPeriod) {
                    this.lt = (BillingPeriod) args.get("lt");
                } else {
                    this.lt = BillingPeriod.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof BillingPeriod) {
                    this.lte = (BillingPeriod) args.get("lte");
                } else {
                    this.lte = BillingPeriod.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof BillingPeriod) {
                    this.like = (BillingPeriod) args.get("like");
                } else {
                    this.like = BillingPeriod.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof BillingPeriod) {
                    this.notLike = (BillingPeriod) args.get("notLike");
                } else {
                    this.notLike = BillingPeriod.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof BillingPeriod) {
                    this.iLike = (BillingPeriod) args.get("iLike");
                } else {
                    this.iLike = BillingPeriod.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof BillingPeriod) {
                    this.notILike = (BillingPeriod) args.get("notILike");
                } else {
                    this.notILike = BillingPeriod.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<BillingPeriod>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<BillingPeriod>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public BillingPeriod getEq() { return this.eq; }
        public BillingPeriod getNeq() { return this.neq; }
        public BillingPeriod getGt() { return this.gt; }
        public BillingPeriod getGte() { return this.gte; }
        public BillingPeriod getLt() { return this.lt; }
        public BillingPeriod getLte() { return this.lte; }
        public BillingPeriod getLike() { return this.like; }
        public BillingPeriod getNotLike() { return this.notLike; }
        public BillingPeriod getILike() { return this.iLike; }
        public BillingPeriod getNotILike() { return this.notILike; }
        public Iterable<BillingPeriod> getIn() { return this.in; }
        public Iterable<BillingPeriod> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(BillingPeriod eq) { this.eq = eq; }
        public void setNeq(BillingPeriod neq) { this.neq = neq; }
        public void setGt(BillingPeriod gt) { this.gt = gt; }
        public void setGte(BillingPeriod gte) { this.gte = gte; }
        public void setLt(BillingPeriod lt) { this.lt = lt; }
        public void setLte(BillingPeriod lte) { this.lte = lte; }
        public void setLike(BillingPeriod like) { this.like = like; }
        public void setNotLike(BillingPeriod notLike) { this.notLike = notLike; }
        public void setILike(BillingPeriod iLike) { this.iLike = iLike; }
        public void setNotILike(BillingPeriod notILike) { this.notILike = notILike; }
        public void setIn(Iterable<BillingPeriod> in) { this.in = in; }
        public void setNotIn(Iterable<BillingPeriod> notIn) { this.notIn = notIn; }
    }
    public static class BillingModelFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private BillingModel eq;
        private BillingModel neq;
        private BillingModel gt;
        private BillingModel gte;
        private BillingModel lt;
        private BillingModel lte;
        private BillingModel like;
        private BillingModel notLike;
        private BillingModel iLike;
        private BillingModel notILike;
        private Iterable<BillingModel> in;
        private Iterable<BillingModel> notIn;

        public BillingModelFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof BillingModel) {
                    this.eq = (BillingModel) args.get("eq");
                } else {
                    this.eq = BillingModel.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof BillingModel) {
                    this.neq = (BillingModel) args.get("neq");
                } else {
                    this.neq = BillingModel.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof BillingModel) {
                    this.gt = (BillingModel) args.get("gt");
                } else {
                    this.gt = BillingModel.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof BillingModel) {
                    this.gte = (BillingModel) args.get("gte");
                } else {
                    this.gte = BillingModel.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof BillingModel) {
                    this.lt = (BillingModel) args.get("lt");
                } else {
                    this.lt = BillingModel.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof BillingModel) {
                    this.lte = (BillingModel) args.get("lte");
                } else {
                    this.lte = BillingModel.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof BillingModel) {
                    this.like = (BillingModel) args.get("like");
                } else {
                    this.like = BillingModel.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof BillingModel) {
                    this.notLike = (BillingModel) args.get("notLike");
                } else {
                    this.notLike = BillingModel.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof BillingModel) {
                    this.iLike = (BillingModel) args.get("iLike");
                } else {
                    this.iLike = BillingModel.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof BillingModel) {
                    this.notILike = (BillingModel) args.get("notILike");
                } else {
                    this.notILike = BillingModel.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<BillingModel>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<BillingModel>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public BillingModel getEq() { return this.eq; }
        public BillingModel getNeq() { return this.neq; }
        public BillingModel getGt() { return this.gt; }
        public BillingModel getGte() { return this.gte; }
        public BillingModel getLt() { return this.lt; }
        public BillingModel getLte() { return this.lte; }
        public BillingModel getLike() { return this.like; }
        public BillingModel getNotLike() { return this.notLike; }
        public BillingModel getILike() { return this.iLike; }
        public BillingModel getNotILike() { return this.notILike; }
        public Iterable<BillingModel> getIn() { return this.in; }
        public Iterable<BillingModel> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(BillingModel eq) { this.eq = eq; }
        public void setNeq(BillingModel neq) { this.neq = neq; }
        public void setGt(BillingModel gt) { this.gt = gt; }
        public void setGte(BillingModel gte) { this.gte = gte; }
        public void setLt(BillingModel lt) { this.lt = lt; }
        public void setLte(BillingModel lte) { this.lte = lte; }
        public void setLike(BillingModel like) { this.like = like; }
        public void setNotLike(BillingModel notLike) { this.notLike = notLike; }
        public void setILike(BillingModel iLike) { this.iLike = iLike; }
        public void setNotILike(BillingModel notILike) { this.notILike = notILike; }
        public void setIn(Iterable<BillingModel> in) { this.in = in; }
        public void setNotIn(Iterable<BillingModel> notIn) { this.notIn = notIn; }
    }
    public static class TiersModeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private TiersMode eq;
        private TiersMode neq;
        private TiersMode gt;
        private TiersMode gte;
        private TiersMode lt;
        private TiersMode lte;
        private TiersMode like;
        private TiersMode notLike;
        private TiersMode iLike;
        private TiersMode notILike;
        private Iterable<TiersMode> in;
        private Iterable<TiersMode> notIn;

        public TiersModeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof TiersMode) {
                    this.eq = (TiersMode) args.get("eq");
                } else {
                    this.eq = TiersMode.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof TiersMode) {
                    this.neq = (TiersMode) args.get("neq");
                } else {
                    this.neq = TiersMode.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof TiersMode) {
                    this.gt = (TiersMode) args.get("gt");
                } else {
                    this.gt = TiersMode.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof TiersMode) {
                    this.gte = (TiersMode) args.get("gte");
                } else {
                    this.gte = TiersMode.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof TiersMode) {
                    this.lt = (TiersMode) args.get("lt");
                } else {
                    this.lt = TiersMode.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof TiersMode) {
                    this.lte = (TiersMode) args.get("lte");
                } else {
                    this.lte = TiersMode.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof TiersMode) {
                    this.like = (TiersMode) args.get("like");
                } else {
                    this.like = TiersMode.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof TiersMode) {
                    this.notLike = (TiersMode) args.get("notLike");
                } else {
                    this.notLike = TiersMode.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof TiersMode) {
                    this.iLike = (TiersMode) args.get("iLike");
                } else {
                    this.iLike = TiersMode.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof TiersMode) {
                    this.notILike = (TiersMode) args.get("notILike");
                } else {
                    this.notILike = TiersMode.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<TiersMode>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<TiersMode>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public TiersMode getEq() { return this.eq; }
        public TiersMode getNeq() { return this.neq; }
        public TiersMode getGt() { return this.gt; }
        public TiersMode getGte() { return this.gte; }
        public TiersMode getLt() { return this.lt; }
        public TiersMode getLte() { return this.lte; }
        public TiersMode getLike() { return this.like; }
        public TiersMode getNotLike() { return this.notLike; }
        public TiersMode getILike() { return this.iLike; }
        public TiersMode getNotILike() { return this.notILike; }
        public Iterable<TiersMode> getIn() { return this.in; }
        public Iterable<TiersMode> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(TiersMode eq) { this.eq = eq; }
        public void setNeq(TiersMode neq) { this.neq = neq; }
        public void setGt(TiersMode gt) { this.gt = gt; }
        public void setGte(TiersMode gte) { this.gte = gte; }
        public void setLt(TiersMode lt) { this.lt = lt; }
        public void setLte(TiersMode lte) { this.lte = lte; }
        public void setLike(TiersMode like) { this.like = like; }
        public void setNotLike(TiersMode notLike) { this.notLike = notLike; }
        public void setILike(TiersMode iLike) { this.iLike = iLike; }
        public void setNotILike(TiersMode notILike) { this.notILike = notILike; }
        public void setIn(Iterable<TiersMode> in) { this.in = in; }
        public void setNotIn(Iterable<TiersMode> notIn) { this.notIn = notIn; }
    }
    public static class PriceFilterPackageDtoFilterInput {
        private Iterable<PriceFilterPackageDtoFilterInput> and;
        private Iterable<PriceFilterPackageDtoFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public PriceFilterPackageDtoFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PriceFilterPackageDtoFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PriceFilterPackageDtoFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<PriceFilterPackageDtoFilterInput> getAnd() { return this.and; }
        public Iterable<PriceFilterPackageDtoFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<PriceFilterPackageDtoFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PriceFilterPackageDtoFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class PackageStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private PackageStatus eq;
        private PackageStatus neq;
        private PackageStatus gt;
        private PackageStatus gte;
        private PackageStatus lt;
        private PackageStatus lte;
        private PackageStatus like;
        private PackageStatus notLike;
        private PackageStatus iLike;
        private PackageStatus notILike;
        private Iterable<PackageStatus> in;
        private Iterable<PackageStatus> notIn;

        public PackageStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof PackageStatus) {
                    this.eq = (PackageStatus) args.get("eq");
                } else {
                    this.eq = PackageStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof PackageStatus) {
                    this.neq = (PackageStatus) args.get("neq");
                } else {
                    this.neq = PackageStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof PackageStatus) {
                    this.gt = (PackageStatus) args.get("gt");
                } else {
                    this.gt = PackageStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof PackageStatus) {
                    this.gte = (PackageStatus) args.get("gte");
                } else {
                    this.gte = PackageStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof PackageStatus) {
                    this.lt = (PackageStatus) args.get("lt");
                } else {
                    this.lt = PackageStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof PackageStatus) {
                    this.lte = (PackageStatus) args.get("lte");
                } else {
                    this.lte = PackageStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof PackageStatus) {
                    this.like = (PackageStatus) args.get("like");
                } else {
                    this.like = PackageStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof PackageStatus) {
                    this.notLike = (PackageStatus) args.get("notLike");
                } else {
                    this.notLike = PackageStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof PackageStatus) {
                    this.iLike = (PackageStatus) args.get("iLike");
                } else {
                    this.iLike = PackageStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof PackageStatus) {
                    this.notILike = (PackageStatus) args.get("notILike");
                } else {
                    this.notILike = PackageStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<PackageStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<PackageStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public PackageStatus getEq() { return this.eq; }
        public PackageStatus getNeq() { return this.neq; }
        public PackageStatus getGt() { return this.gt; }
        public PackageStatus getGte() { return this.gte; }
        public PackageStatus getLt() { return this.lt; }
        public PackageStatus getLte() { return this.lte; }
        public PackageStatus getLike() { return this.like; }
        public PackageStatus getNotLike() { return this.notLike; }
        public PackageStatus getILike() { return this.iLike; }
        public PackageStatus getNotILike() { return this.notILike; }
        public Iterable<PackageStatus> getIn() { return this.in; }
        public Iterable<PackageStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(PackageStatus eq) { this.eq = eq; }
        public void setNeq(PackageStatus neq) { this.neq = neq; }
        public void setGt(PackageStatus gt) { this.gt = gt; }
        public void setGte(PackageStatus gte) { this.gte = gte; }
        public void setLt(PackageStatus lt) { this.lt = lt; }
        public void setLte(PackageStatus lte) { this.lte = lte; }
        public void setLike(PackageStatus like) { this.like = like; }
        public void setNotLike(PackageStatus notLike) { this.notLike = notLike; }
        public void setILike(PackageStatus iLike) { this.iLike = iLike; }
        public void setNotILike(PackageStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<PackageStatus> in) { this.in = in; }
        public void setNotIn(Iterable<PackageStatus> notIn) { this.notIn = notIn; }
    }
    public static class PricingTypeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private PricingType eq;
        private PricingType neq;
        private PricingType gt;
        private PricingType gte;
        private PricingType lt;
        private PricingType lte;
        private PricingType like;
        private PricingType notLike;
        private PricingType iLike;
        private PricingType notILike;
        private Iterable<PricingType> in;
        private Iterable<PricingType> notIn;

        public PricingTypeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof PricingType) {
                    this.eq = (PricingType) args.get("eq");
                } else {
                    this.eq = PricingType.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof PricingType) {
                    this.neq = (PricingType) args.get("neq");
                } else {
                    this.neq = PricingType.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof PricingType) {
                    this.gt = (PricingType) args.get("gt");
                } else {
                    this.gt = PricingType.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof PricingType) {
                    this.gte = (PricingType) args.get("gte");
                } else {
                    this.gte = PricingType.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof PricingType) {
                    this.lt = (PricingType) args.get("lt");
                } else {
                    this.lt = PricingType.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof PricingType) {
                    this.lte = (PricingType) args.get("lte");
                } else {
                    this.lte = PricingType.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof PricingType) {
                    this.like = (PricingType) args.get("like");
                } else {
                    this.like = PricingType.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof PricingType) {
                    this.notLike = (PricingType) args.get("notLike");
                } else {
                    this.notLike = PricingType.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof PricingType) {
                    this.iLike = (PricingType) args.get("iLike");
                } else {
                    this.iLike = PricingType.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof PricingType) {
                    this.notILike = (PricingType) args.get("notILike");
                } else {
                    this.notILike = PricingType.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<PricingType>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<PricingType>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public PricingType getEq() { return this.eq; }
        public PricingType getNeq() { return this.neq; }
        public PricingType getGt() { return this.gt; }
        public PricingType getGte() { return this.gte; }
        public PricingType getLt() { return this.lt; }
        public PricingType getLte() { return this.lte; }
        public PricingType getLike() { return this.like; }
        public PricingType getNotLike() { return this.notLike; }
        public PricingType getILike() { return this.iLike; }
        public PricingType getNotILike() { return this.notILike; }
        public Iterable<PricingType> getIn() { return this.in; }
        public Iterable<PricingType> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(PricingType eq) { this.eq = eq; }
        public void setNeq(PricingType neq) { this.neq = neq; }
        public void setGt(PricingType gt) { this.gt = gt; }
        public void setGte(PricingType gte) { this.gte = gte; }
        public void setLt(PricingType lt) { this.lt = lt; }
        public void setLte(PricingType lte) { this.lte = lte; }
        public void setLike(PricingType like) { this.like = like; }
        public void setNotLike(PricingType notLike) { this.notLike = notLike; }
        public void setILike(PricingType iLike) { this.iLike = iLike; }
        public void setNotILike(PricingType notILike) { this.notILike = notILike; }
        public void setIn(Iterable<PricingType> in) { this.in = in; }
        public void setNotIn(Iterable<PricingType> notIn) { this.notIn = notIn; }
    }
    public static class BooleanFieldComparisonInput {
        private Boolean is;
        private Boolean isNot;

        public BooleanFieldComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
    }
    public static class IntFieldComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private Integer eq;
        private Integer neq;
        private Integer gt;
        private Integer gte;
        private Integer lt;
        private Integer lte;
        private Iterable<Integer> in;
        private Iterable<Integer> notIn;
        private IntFieldComparisonBetweenInput between;
        private IntFieldComparisonBetweenInput notBetween;

        public IntFieldComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                this.eq = (Integer) args.get("eq");
                this.neq = (Integer) args.get("neq");
                this.gt = (Integer) args.get("gt");
                this.gte = (Integer) args.get("gte");
                this.lt = (Integer) args.get("lt");
                this.lte = (Integer) args.get("lte");
                this.in = (Iterable<Integer>) args.get("in");
                this.notIn = (Iterable<Integer>) args.get("notIn");
                this.between = new IntFieldComparisonBetweenInput((Map<String, Object>) args.get("between"));
                this.notBetween = new IntFieldComparisonBetweenInput((Map<String, Object>) args.get("notBetween"));
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public Integer getEq() { return this.eq; }
        public Integer getNeq() { return this.neq; }
        public Integer getGt() { return this.gt; }
        public Integer getGte() { return this.gte; }
        public Integer getLt() { return this.lt; }
        public Integer getLte() { return this.lte; }
        public Iterable<Integer> getIn() { return this.in; }
        public Iterable<Integer> getNotIn() { return this.notIn; }
        public IntFieldComparisonBetweenInput getBetween() { return this.between; }
        public IntFieldComparisonBetweenInput getNotBetween() { return this.notBetween; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(Integer eq) { this.eq = eq; }
        public void setNeq(Integer neq) { this.neq = neq; }
        public void setGt(Integer gt) { this.gt = gt; }
        public void setGte(Integer gte) { this.gte = gte; }
        public void setLt(Integer lt) { this.lt = lt; }
        public void setLte(Integer lte) { this.lte = lte; }
        public void setIn(Iterable<Integer> in) { this.in = in; }
        public void setNotIn(Iterable<Integer> notIn) { this.notIn = notIn; }
        public void setBetween(IntFieldComparisonBetweenInput between) { this.between = between; }
        public void setNotBetween(IntFieldComparisonBetweenInput notBetween) { this.notBetween = notBetween; }
    }
    public static class IntFieldComparisonBetweenInput {
        private Integer lower;
        private Integer upper;

        public IntFieldComparisonBetweenInput(Map<String, Object> args) {
            if (args != null) {
                this.lower = (Integer) args.get("lower");
                this.upper = (Integer) args.get("upper");
            }
        }

        public Integer getLower() { return this.lower; }
        public Integer getUpper() { return this.upper; }
        public void setLower(Integer lower) { this.lower = lower; }
        public void setUpper(Integer upper) { this.upper = upper; }
    }
    public static class PriceSortInput {
        private PriceSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public PriceSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof PriceSortFields) {
                    this.field = (PriceSortFields) args.get("field");
                } else {
                    this.field = PriceSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public PriceSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(PriceSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum PriceSortFields {
        id,
        createdAt,
        billingPeriod,
        billingModel,
        tiersMode,
        billingId

    }

    public static class PlanInheritedEntitlementsArgs {
        private Boolean includeOverridden;

        public PlanInheritedEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.includeOverridden = (Boolean) args.get("includeOverridden");
            }
        }

        public Boolean getIncludeOverridden() { return this.includeOverridden; }
        public void setIncludeOverridden(Boolean includeOverridden) { this.includeOverridden = includeOverridden; }
    }
    public static class PlanCompatibleAddonsArgs {
        private AddonFilterInput filter;
        private Iterable<AddonSortInput> sorting;

        public PlanCompatibleAddonsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new AddonFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<AddonSortInput>) args.get("sorting");
                }
            }
        }

        public AddonFilterInput getFilter() { return this.filter; }
        public Iterable<AddonSortInput> getSorting() { return this.sorting; }
        public void setFilter(AddonFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<AddonSortInput> sorting) { this.sorting = sorting; }
    }
    public static class AddonFilterInput {
        private Iterable<AddonFilterInput> and;
        private Iterable<AddonFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public AddonFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<AddonFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<AddonFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<AddonFilterInput> getAnd() { return this.and; }
        public Iterable<AddonFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<AddonFilterInput> and) { this.and = and; }
        public void setOr(Iterable<AddonFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class AddonSortInput {
        private AddonSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public AddonSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof AddonSortFields) {
                    this.field = (AddonSortFields) args.get("field");
                } else {
                    this.field = AddonSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public AddonSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(AddonSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum AddonSortFields {
        id,
        createdAt,
        updatedAt,
        refId,
        billingId,
        displayName,
        status,
        pricingType,
        description,
        environmentId,
        productId,
        isLatest,
        versionNumber

    }


    /** Promotional entitlement status */
    public enum PromotionalEntitlementStatus {
        Active,
        Expired,
        Paused

    }

    /** Promotional entitlement duration */
    public enum PromotionalEntitlementPeriod {
        ONE_WEEK,
        ONE_MONTH,
        SIX_MONTH,
        ONE_YEAR,
        LIFETIME,
        CUSTOM

    }

    public static class CouponCustomersArgs {
        private CustomerFilterInput filter;
        private Iterable<CustomerSortInput> sorting;

        public CouponCustomersArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new CustomerFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerSortInput>) args.get("sorting");
                }
            }
        }

        public CustomerFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerSortInput> getSorting() { return this.sorting; }
        public void setFilter(CustomerFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerSortInput> sorting) { this.sorting = sorting; }
    }
    /** The type of the coupon */
    public enum CouponType {
        FIXED,
        PERCENTAGE

    }

    /** The status of the coupon */
    public enum CouponStatus {
        ACTIVE,
        ARCHIVED

    }

    public static class CustomerFilterInput {
        private Iterable<CustomerFilterInput> and;
        private Iterable<CustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;
        private CustomerFilterPromotionalEntitlementFilterInput promotionalEntitlements;
        private CustomerFilterCustomerSubscriptionFilterInput subscriptions;

        public CustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
                this.promotionalEntitlements = new CustomerFilterPromotionalEntitlementFilterInput((Map<String, Object>) args.get("promotionalEntitlements"));
                this.subscriptions = new CustomerFilterCustomerSubscriptionFilterInput((Map<String, Object>) args.get("subscriptions"));
            }
        }

        public Iterable<CustomerFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public CustomerFilterPromotionalEntitlementFilterInput getPromotionalEntitlements() { return this.promotionalEntitlements; }
        public CustomerFilterCustomerSubscriptionFilterInput getSubscriptions() { return this.subscriptions; }
        public void setAnd(Iterable<CustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
        public void setPromotionalEntitlements(CustomerFilterPromotionalEntitlementFilterInput promotionalEntitlements) { this.promotionalEntitlements = promotionalEntitlements; }
        public void setSubscriptions(CustomerFilterCustomerSubscriptionFilterInput subscriptions) { this.subscriptions = subscriptions; }
    }
    public static class CustomerSearchQueryFilterComparisonInput {
        private String iLike;

        public CustomerSearchQueryFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.iLike = (String) args.get("iLike");
            }
        }

        public String getILike() { return this.iLike; }
        public void setILike(String iLike) { this.iLike = iLike; }
    }
    public static class CustomerFilterPromotionalEntitlementFilterInput {
        private Iterable<CustomerFilterPromotionalEntitlementFilterInput> and;
        private Iterable<CustomerFilterPromotionalEntitlementFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private PromotionalEntitlementStatusFilterComparisonInput status;
        private StringFieldComparisonInput environmentId;

        public CustomerFilterPromotionalEntitlementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerFilterPromotionalEntitlementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerFilterPromotionalEntitlementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.status = new PromotionalEntitlementStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<CustomerFilterPromotionalEntitlementFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerFilterPromotionalEntitlementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public PromotionalEntitlementStatusFilterComparisonInput getStatus() { return this.status; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<CustomerFilterPromotionalEntitlementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerFilterPromotionalEntitlementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setStatus(PromotionalEntitlementStatusFilterComparisonInput status) { this.status = status; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class PromotionalEntitlementStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private PromotionalEntitlementStatus eq;
        private PromotionalEntitlementStatus neq;
        private PromotionalEntitlementStatus gt;
        private PromotionalEntitlementStatus gte;
        private PromotionalEntitlementStatus lt;
        private PromotionalEntitlementStatus lte;
        private PromotionalEntitlementStatus like;
        private PromotionalEntitlementStatus notLike;
        private PromotionalEntitlementStatus iLike;
        private PromotionalEntitlementStatus notILike;
        private Iterable<PromotionalEntitlementStatus> in;
        private Iterable<PromotionalEntitlementStatus> notIn;

        public PromotionalEntitlementStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof PromotionalEntitlementStatus) {
                    this.eq = (PromotionalEntitlementStatus) args.get("eq");
                } else {
                    this.eq = PromotionalEntitlementStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof PromotionalEntitlementStatus) {
                    this.neq = (PromotionalEntitlementStatus) args.get("neq");
                } else {
                    this.neq = PromotionalEntitlementStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof PromotionalEntitlementStatus) {
                    this.gt = (PromotionalEntitlementStatus) args.get("gt");
                } else {
                    this.gt = PromotionalEntitlementStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof PromotionalEntitlementStatus) {
                    this.gte = (PromotionalEntitlementStatus) args.get("gte");
                } else {
                    this.gte = PromotionalEntitlementStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof PromotionalEntitlementStatus) {
                    this.lt = (PromotionalEntitlementStatus) args.get("lt");
                } else {
                    this.lt = PromotionalEntitlementStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof PromotionalEntitlementStatus) {
                    this.lte = (PromotionalEntitlementStatus) args.get("lte");
                } else {
                    this.lte = PromotionalEntitlementStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof PromotionalEntitlementStatus) {
                    this.like = (PromotionalEntitlementStatus) args.get("like");
                } else {
                    this.like = PromotionalEntitlementStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof PromotionalEntitlementStatus) {
                    this.notLike = (PromotionalEntitlementStatus) args.get("notLike");
                } else {
                    this.notLike = PromotionalEntitlementStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof PromotionalEntitlementStatus) {
                    this.iLike = (PromotionalEntitlementStatus) args.get("iLike");
                } else {
                    this.iLike = PromotionalEntitlementStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof PromotionalEntitlementStatus) {
                    this.notILike = (PromotionalEntitlementStatus) args.get("notILike");
                } else {
                    this.notILike = PromotionalEntitlementStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<PromotionalEntitlementStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<PromotionalEntitlementStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public PromotionalEntitlementStatus getEq() { return this.eq; }
        public PromotionalEntitlementStatus getNeq() { return this.neq; }
        public PromotionalEntitlementStatus getGt() { return this.gt; }
        public PromotionalEntitlementStatus getGte() { return this.gte; }
        public PromotionalEntitlementStatus getLt() { return this.lt; }
        public PromotionalEntitlementStatus getLte() { return this.lte; }
        public PromotionalEntitlementStatus getLike() { return this.like; }
        public PromotionalEntitlementStatus getNotLike() { return this.notLike; }
        public PromotionalEntitlementStatus getILike() { return this.iLike; }
        public PromotionalEntitlementStatus getNotILike() { return this.notILike; }
        public Iterable<PromotionalEntitlementStatus> getIn() { return this.in; }
        public Iterable<PromotionalEntitlementStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(PromotionalEntitlementStatus eq) { this.eq = eq; }
        public void setNeq(PromotionalEntitlementStatus neq) { this.neq = neq; }
        public void setGt(PromotionalEntitlementStatus gt) { this.gt = gt; }
        public void setGte(PromotionalEntitlementStatus gte) { this.gte = gte; }
        public void setLt(PromotionalEntitlementStatus lt) { this.lt = lt; }
        public void setLte(PromotionalEntitlementStatus lte) { this.lte = lte; }
        public void setLike(PromotionalEntitlementStatus like) { this.like = like; }
        public void setNotLike(PromotionalEntitlementStatus notLike) { this.notLike = notLike; }
        public void setILike(PromotionalEntitlementStatus iLike) { this.iLike = iLike; }
        public void setNotILike(PromotionalEntitlementStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<PromotionalEntitlementStatus> in) { this.in = in; }
        public void setNotIn(Iterable<PromotionalEntitlementStatus> notIn) { this.notIn = notIn; }
    }
    public static class CustomerFilterCustomerSubscriptionFilterInput {
        private Iterable<CustomerFilterCustomerSubscriptionFilterInput> and;
        private Iterable<CustomerFilterCustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;

        public CustomerFilterCustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerFilterCustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerFilterCustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
            }
        }

        public Iterable<CustomerFilterCustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerFilterCustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public void setAnd(Iterable<CustomerFilterCustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerFilterCustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
    }
    public static class SubscriptionStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private SubscriptionStatus eq;
        private SubscriptionStatus neq;
        private SubscriptionStatus gt;
        private SubscriptionStatus gte;
        private SubscriptionStatus lt;
        private SubscriptionStatus lte;
        private SubscriptionStatus like;
        private SubscriptionStatus notLike;
        private SubscriptionStatus iLike;
        private SubscriptionStatus notILike;
        private Iterable<SubscriptionStatus> in;
        private Iterable<SubscriptionStatus> notIn;

        public SubscriptionStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof SubscriptionStatus) {
                    this.eq = (SubscriptionStatus) args.get("eq");
                } else {
                    this.eq = SubscriptionStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof SubscriptionStatus) {
                    this.neq = (SubscriptionStatus) args.get("neq");
                } else {
                    this.neq = SubscriptionStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof SubscriptionStatus) {
                    this.gt = (SubscriptionStatus) args.get("gt");
                } else {
                    this.gt = SubscriptionStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof SubscriptionStatus) {
                    this.gte = (SubscriptionStatus) args.get("gte");
                } else {
                    this.gte = SubscriptionStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof SubscriptionStatus) {
                    this.lt = (SubscriptionStatus) args.get("lt");
                } else {
                    this.lt = SubscriptionStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof SubscriptionStatus) {
                    this.lte = (SubscriptionStatus) args.get("lte");
                } else {
                    this.lte = SubscriptionStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof SubscriptionStatus) {
                    this.like = (SubscriptionStatus) args.get("like");
                } else {
                    this.like = SubscriptionStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof SubscriptionStatus) {
                    this.notLike = (SubscriptionStatus) args.get("notLike");
                } else {
                    this.notLike = SubscriptionStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof SubscriptionStatus) {
                    this.iLike = (SubscriptionStatus) args.get("iLike");
                } else {
                    this.iLike = SubscriptionStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof SubscriptionStatus) {
                    this.notILike = (SubscriptionStatus) args.get("notILike");
                } else {
                    this.notILike = SubscriptionStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<SubscriptionStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<SubscriptionStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public SubscriptionStatus getEq() { return this.eq; }
        public SubscriptionStatus getNeq() { return this.neq; }
        public SubscriptionStatus getGt() { return this.gt; }
        public SubscriptionStatus getGte() { return this.gte; }
        public SubscriptionStatus getLt() { return this.lt; }
        public SubscriptionStatus getLte() { return this.lte; }
        public SubscriptionStatus getLike() { return this.like; }
        public SubscriptionStatus getNotLike() { return this.notLike; }
        public SubscriptionStatus getILike() { return this.iLike; }
        public SubscriptionStatus getNotILike() { return this.notILike; }
        public Iterable<SubscriptionStatus> getIn() { return this.in; }
        public Iterable<SubscriptionStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(SubscriptionStatus eq) { this.eq = eq; }
        public void setNeq(SubscriptionStatus neq) { this.neq = neq; }
        public void setGt(SubscriptionStatus gt) { this.gt = gt; }
        public void setGte(SubscriptionStatus gte) { this.gte = gte; }
        public void setLt(SubscriptionStatus lt) { this.lt = lt; }
        public void setLte(SubscriptionStatus lte) { this.lte = lte; }
        public void setLike(SubscriptionStatus like) { this.like = like; }
        public void setNotLike(SubscriptionStatus notLike) { this.notLike = notLike; }
        public void setILike(SubscriptionStatus iLike) { this.iLike = iLike; }
        public void setNotILike(SubscriptionStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<SubscriptionStatus> in) { this.in = in; }
        public void setNotIn(Iterable<SubscriptionStatus> notIn) { this.notIn = notIn; }
    }
    /** Subscription status */
    public enum SubscriptionStatus {
        PAYMENT_PENDING,
        ACTIVE,
        EXPIRED,
        IN_TRIAL,
        CANCELED,
        NOT_STARTED

    }

    public static class SubscriptionCancelReasonFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private SubscriptionCancelReason eq;
        private SubscriptionCancelReason neq;
        private SubscriptionCancelReason gt;
        private SubscriptionCancelReason gte;
        private SubscriptionCancelReason lt;
        private SubscriptionCancelReason lte;
        private SubscriptionCancelReason like;
        private SubscriptionCancelReason notLike;
        private SubscriptionCancelReason iLike;
        private SubscriptionCancelReason notILike;
        private Iterable<SubscriptionCancelReason> in;
        private Iterable<SubscriptionCancelReason> notIn;

        public SubscriptionCancelReasonFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof SubscriptionCancelReason) {
                    this.eq = (SubscriptionCancelReason) args.get("eq");
                } else {
                    this.eq = SubscriptionCancelReason.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof SubscriptionCancelReason) {
                    this.neq = (SubscriptionCancelReason) args.get("neq");
                } else {
                    this.neq = SubscriptionCancelReason.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof SubscriptionCancelReason) {
                    this.gt = (SubscriptionCancelReason) args.get("gt");
                } else {
                    this.gt = SubscriptionCancelReason.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof SubscriptionCancelReason) {
                    this.gte = (SubscriptionCancelReason) args.get("gte");
                } else {
                    this.gte = SubscriptionCancelReason.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof SubscriptionCancelReason) {
                    this.lt = (SubscriptionCancelReason) args.get("lt");
                } else {
                    this.lt = SubscriptionCancelReason.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof SubscriptionCancelReason) {
                    this.lte = (SubscriptionCancelReason) args.get("lte");
                } else {
                    this.lte = SubscriptionCancelReason.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof SubscriptionCancelReason) {
                    this.like = (SubscriptionCancelReason) args.get("like");
                } else {
                    this.like = SubscriptionCancelReason.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof SubscriptionCancelReason) {
                    this.notLike = (SubscriptionCancelReason) args.get("notLike");
                } else {
                    this.notLike = SubscriptionCancelReason.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof SubscriptionCancelReason) {
                    this.iLike = (SubscriptionCancelReason) args.get("iLike");
                } else {
                    this.iLike = SubscriptionCancelReason.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof SubscriptionCancelReason) {
                    this.notILike = (SubscriptionCancelReason) args.get("notILike");
                } else {
                    this.notILike = SubscriptionCancelReason.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<SubscriptionCancelReason>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<SubscriptionCancelReason>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public SubscriptionCancelReason getEq() { return this.eq; }
        public SubscriptionCancelReason getNeq() { return this.neq; }
        public SubscriptionCancelReason getGt() { return this.gt; }
        public SubscriptionCancelReason getGte() { return this.gte; }
        public SubscriptionCancelReason getLt() { return this.lt; }
        public SubscriptionCancelReason getLte() { return this.lte; }
        public SubscriptionCancelReason getLike() { return this.like; }
        public SubscriptionCancelReason getNotLike() { return this.notLike; }
        public SubscriptionCancelReason getILike() { return this.iLike; }
        public SubscriptionCancelReason getNotILike() { return this.notILike; }
        public Iterable<SubscriptionCancelReason> getIn() { return this.in; }
        public Iterable<SubscriptionCancelReason> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(SubscriptionCancelReason eq) { this.eq = eq; }
        public void setNeq(SubscriptionCancelReason neq) { this.neq = neq; }
        public void setGt(SubscriptionCancelReason gt) { this.gt = gt; }
        public void setGte(SubscriptionCancelReason gte) { this.gte = gte; }
        public void setLt(SubscriptionCancelReason lt) { this.lt = lt; }
        public void setLte(SubscriptionCancelReason lte) { this.lte = lte; }
        public void setLike(SubscriptionCancelReason like) { this.like = like; }
        public void setNotLike(SubscriptionCancelReason notLike) { this.notLike = notLike; }
        public void setILike(SubscriptionCancelReason iLike) { this.iLike = iLike; }
        public void setNotILike(SubscriptionCancelReason notILike) { this.notILike = notILike; }
        public void setIn(Iterable<SubscriptionCancelReason> in) { this.in = in; }
        public void setNotIn(Iterable<SubscriptionCancelReason> notIn) { this.notIn = notIn; }
    }
    /** Subscription cancellation status */
    public enum SubscriptionCancelReason {
        UpgradeOrDowngrade,
        CancelledByBilling,
        Expired,
        DetachBilling,
        TrialEnded,
        Immediate,
        TrialConverted,
        PendingPaymentExpired,
        ScheduledCancellation,
        CustomerArchived

    }

    public static class PaymentCollectionFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private PaymentCollection eq;
        private PaymentCollection neq;
        private PaymentCollection gt;
        private PaymentCollection gte;
        private PaymentCollection lt;
        private PaymentCollection lte;
        private PaymentCollection like;
        private PaymentCollection notLike;
        private PaymentCollection iLike;
        private PaymentCollection notILike;
        private Iterable<PaymentCollection> in;
        private Iterable<PaymentCollection> notIn;

        public PaymentCollectionFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof PaymentCollection) {
                    this.eq = (PaymentCollection) args.get("eq");
                } else {
                    this.eq = PaymentCollection.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof PaymentCollection) {
                    this.neq = (PaymentCollection) args.get("neq");
                } else {
                    this.neq = PaymentCollection.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof PaymentCollection) {
                    this.gt = (PaymentCollection) args.get("gt");
                } else {
                    this.gt = PaymentCollection.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof PaymentCollection) {
                    this.gte = (PaymentCollection) args.get("gte");
                } else {
                    this.gte = PaymentCollection.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof PaymentCollection) {
                    this.lt = (PaymentCollection) args.get("lt");
                } else {
                    this.lt = PaymentCollection.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof PaymentCollection) {
                    this.lte = (PaymentCollection) args.get("lte");
                } else {
                    this.lte = PaymentCollection.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof PaymentCollection) {
                    this.like = (PaymentCollection) args.get("like");
                } else {
                    this.like = PaymentCollection.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof PaymentCollection) {
                    this.notLike = (PaymentCollection) args.get("notLike");
                } else {
                    this.notLike = PaymentCollection.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof PaymentCollection) {
                    this.iLike = (PaymentCollection) args.get("iLike");
                } else {
                    this.iLike = PaymentCollection.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof PaymentCollection) {
                    this.notILike = (PaymentCollection) args.get("notILike");
                } else {
                    this.notILike = PaymentCollection.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<PaymentCollection>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<PaymentCollection>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public PaymentCollection getEq() { return this.eq; }
        public PaymentCollection getNeq() { return this.neq; }
        public PaymentCollection getGt() { return this.gt; }
        public PaymentCollection getGte() { return this.gte; }
        public PaymentCollection getLt() { return this.lt; }
        public PaymentCollection getLte() { return this.lte; }
        public PaymentCollection getLike() { return this.like; }
        public PaymentCollection getNotLike() { return this.notLike; }
        public PaymentCollection getILike() { return this.iLike; }
        public PaymentCollection getNotILike() { return this.notILike; }
        public Iterable<PaymentCollection> getIn() { return this.in; }
        public Iterable<PaymentCollection> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(PaymentCollection eq) { this.eq = eq; }
        public void setNeq(PaymentCollection neq) { this.neq = neq; }
        public void setGt(PaymentCollection gt) { this.gt = gt; }
        public void setGte(PaymentCollection gte) { this.gte = gte; }
        public void setLt(PaymentCollection lt) { this.lt = lt; }
        public void setLte(PaymentCollection lte) { this.lte = lte; }
        public void setLike(PaymentCollection like) { this.like = like; }
        public void setNotLike(PaymentCollection notLike) { this.notLike = notLike; }
        public void setILike(PaymentCollection iLike) { this.iLike = iLike; }
        public void setNotILike(PaymentCollection notILike) { this.notILike = notILike; }
        public void setIn(Iterable<PaymentCollection> in) { this.in = in; }
        public void setNotIn(Iterable<PaymentCollection> notIn) { this.notIn = notIn; }
    }
    /** Payment collection */
    public enum PaymentCollection {
        NOT_REQUIRED,
        PROCESSING,
        FAILED,
        ACTION_REQUIRED

    }

    public static class CustomerSortInput {
        private CustomerSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public CustomerSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof CustomerSortFields) {
                    this.field = (CustomerSortFields) args.get("field");
                } else {
                    this.field = CustomerSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public CustomerSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(CustomerSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum CustomerSortFields {
        id,
        name,
        email,
        refId,
        customerId,
        createdAt,
        updatedAt,
        deletedAt,
        environmentId,
        billingId,
        crmId,
        crmHubspotCompanyId,
        crmHubspotCompanyUrl,
        searchQuery

    }



    /** The status of the EXPERIMENT */
    public enum ExperimentStatus {
        DRAFT,
        IN_PROGRESS,
        COMPLETED

    }

    /** The group of the experiment */
    public enum ExperimentGroupType {
        CONTROL,
        VARIANT

    }



    public static class CustomerSubscriptionsArgs {
        private CustomerSubscriptionFilterInput filter;
        private Iterable<CustomerSubscriptionSortInput> sorting;

        public CustomerSubscriptionsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new CustomerSubscriptionFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerSubscriptionSortInput>) args.get("sorting");
                }
            }
        }

        public CustomerSubscriptionFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerSubscriptionSortInput> getSorting() { return this.sorting; }
        public void setFilter(CustomerSubscriptionFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerSubscriptionSortInput> sorting) { this.sorting = sorting; }
    }
    public static class CustomerPromotionalEntitlementsArgs {
        private PromotionalEntitlementFilterInput filter;
        private Iterable<PromotionalEntitlementSortInput> sorting;

        public CustomerPromotionalEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new PromotionalEntitlementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PromotionalEntitlementSortInput>) args.get("sorting");
                }
            }
        }

        public PromotionalEntitlementFilterInput getFilter() { return this.filter; }
        public Iterable<PromotionalEntitlementSortInput> getSorting() { return this.sorting; }
        public void setFilter(PromotionalEntitlementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PromotionalEntitlementSortInput> sorting) { this.sorting = sorting; }
    }
    /** Type of a payment method */
    public enum PaymentMethodType {
        CARD,
        BANK

    }

    public static class CustomerSubscriptionFilterInput {
        private Iterable<CustomerSubscriptionFilterInput> and;
        private Iterable<CustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;
        private CustomerSubscriptionFilterPlanFilterInput plan;
        private CustomerSubscriptionFilterCustomerResourceFilterInput resource;
        private CustomerSubscriptionFilterCustomerFilterInput customer;
        private CustomerSubscriptionFilterSubscriptionPriceFilterInput prices;
        private CustomerSubscriptionFilterSubscriptionAddonFilterInput addons;
        private CustomerSubscriptionFilterSubscriptionEntitlementFilterInput subscriptionEntitlements;

        public CustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
                this.plan = new CustomerSubscriptionFilterPlanFilterInput((Map<String, Object>) args.get("plan"));
                this.resource = new CustomerSubscriptionFilterCustomerResourceFilterInput((Map<String, Object>) args.get("resource"));
                this.customer = new CustomerSubscriptionFilterCustomerFilterInput((Map<String, Object>) args.get("customer"));
                this.prices = new CustomerSubscriptionFilterSubscriptionPriceFilterInput((Map<String, Object>) args.get("prices"));
                this.addons = new CustomerSubscriptionFilterSubscriptionAddonFilterInput((Map<String, Object>) args.get("addons"));
                this.subscriptionEntitlements = new CustomerSubscriptionFilterSubscriptionEntitlementFilterInput((Map<String, Object>) args.get("subscriptionEntitlements"));
            }
        }

        public Iterable<CustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public CustomerSubscriptionFilterPlanFilterInput getPlan() { return this.plan; }
        public CustomerSubscriptionFilterCustomerResourceFilterInput getResource() { return this.resource; }
        public CustomerSubscriptionFilterCustomerFilterInput getCustomer() { return this.customer; }
        public CustomerSubscriptionFilterSubscriptionPriceFilterInput getPrices() { return this.prices; }
        public CustomerSubscriptionFilterSubscriptionAddonFilterInput getAddons() { return this.addons; }
        public CustomerSubscriptionFilterSubscriptionEntitlementFilterInput getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public void setAnd(Iterable<CustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
        public void setPlan(CustomerSubscriptionFilterPlanFilterInput plan) { this.plan = plan; }
        public void setResource(CustomerSubscriptionFilterCustomerResourceFilterInput resource) { this.resource = resource; }
        public void setCustomer(CustomerSubscriptionFilterCustomerFilterInput customer) { this.customer = customer; }
        public void setPrices(CustomerSubscriptionFilterSubscriptionPriceFilterInput prices) { this.prices = prices; }
        public void setAddons(CustomerSubscriptionFilterSubscriptionAddonFilterInput addons) { this.addons = addons; }
        public void setSubscriptionEntitlements(CustomerSubscriptionFilterSubscriptionEntitlementFilterInput subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
    }
    public static class CustomerSubscriptionFilterPlanFilterInput {
        private Iterable<CustomerSubscriptionFilterPlanFilterInput> and;
        private Iterable<CustomerSubscriptionFilterPlanFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public CustomerSubscriptionFilterPlanFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterPlanFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterPlanFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<CustomerSubscriptionFilterPlanFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterPlanFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<CustomerSubscriptionFilterPlanFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterPlanFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class CustomerSubscriptionFilterCustomerResourceFilterInput {
        private Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> and;
        private Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput resourceId;
        private DateFieldComparisonInput createdAt;

        public CustomerSubscriptionFilterCustomerResourceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
            }
        }

        public Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public void setAnd(Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterCustomerResourceFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
    }
    public static class CustomerSubscriptionFilterCustomerFilterInput {
        private Iterable<CustomerSubscriptionFilterCustomerFilterInput> and;
        private Iterable<CustomerSubscriptionFilterCustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;

        public CustomerSubscriptionFilterCustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterCustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterCustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
            }
        }

        public Iterable<CustomerSubscriptionFilterCustomerFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterCustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public void setAnd(Iterable<CustomerSubscriptionFilterCustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterCustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
    }
    public static class CustomerSubscriptionFilterSubscriptionPriceFilterInput {
        private Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> and;
        private Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private NumberFieldComparisonInput usageLimit;
        private StringFieldComparisonInput featureId;
        private BillingModelFilterComparisonInput billingModel;

        public CustomerSubscriptionFilterSubscriptionPriceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.usageLimit = new NumberFieldComparisonInput((Map<String, Object>) args.get("usageLimit"));
                this.featureId = new StringFieldComparisonInput((Map<String, Object>) args.get("featureId"));
                this.billingModel = new BillingModelFilterComparisonInput((Map<String, Object>) args.get("billingModel"));
            }
        }

        public Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public NumberFieldComparisonInput getUsageLimit() { return this.usageLimit; }
        public StringFieldComparisonInput getFeatureId() { return this.featureId; }
        public BillingModelFilterComparisonInput getBillingModel() { return this.billingModel; }
        public void setAnd(Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterSubscriptionPriceFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setUsageLimit(NumberFieldComparisonInput usageLimit) { this.usageLimit = usageLimit; }
        public void setFeatureId(StringFieldComparisonInput featureId) { this.featureId = featureId; }
        public void setBillingModel(BillingModelFilterComparisonInput billingModel) { this.billingModel = billingModel; }
    }
    public static class NumberFieldComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private Double eq;
        private Double neq;
        private Double gt;
        private Double gte;
        private Double lt;
        private Double lte;
        private Iterable<Double> in;
        private Iterable<Double> notIn;
        private NumberFieldComparisonBetweenInput between;
        private NumberFieldComparisonBetweenInput notBetween;

        public NumberFieldComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                this.eq = (Double) args.get("eq");
                this.neq = (Double) args.get("neq");
                this.gt = (Double) args.get("gt");
                this.gte = (Double) args.get("gte");
                this.lt = (Double) args.get("lt");
                this.lte = (Double) args.get("lte");
                this.in = (Iterable<Double>) args.get("in");
                this.notIn = (Iterable<Double>) args.get("notIn");
                this.between = new NumberFieldComparisonBetweenInput((Map<String, Object>) args.get("between"));
                this.notBetween = new NumberFieldComparisonBetweenInput((Map<String, Object>) args.get("notBetween"));
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public Double getEq() { return this.eq; }
        public Double getNeq() { return this.neq; }
        public Double getGt() { return this.gt; }
        public Double getGte() { return this.gte; }
        public Double getLt() { return this.lt; }
        public Double getLte() { return this.lte; }
        public Iterable<Double> getIn() { return this.in; }
        public Iterable<Double> getNotIn() { return this.notIn; }
        public NumberFieldComparisonBetweenInput getBetween() { return this.between; }
        public NumberFieldComparisonBetweenInput getNotBetween() { return this.notBetween; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(Double eq) { this.eq = eq; }
        public void setNeq(Double neq) { this.neq = neq; }
        public void setGt(Double gt) { this.gt = gt; }
        public void setGte(Double gte) { this.gte = gte; }
        public void setLt(Double lt) { this.lt = lt; }
        public void setLte(Double lte) { this.lte = lte; }
        public void setIn(Iterable<Double> in) { this.in = in; }
        public void setNotIn(Iterable<Double> notIn) { this.notIn = notIn; }
        public void setBetween(NumberFieldComparisonBetweenInput between) { this.between = between; }
        public void setNotBetween(NumberFieldComparisonBetweenInput notBetween) { this.notBetween = notBetween; }
    }
    public static class NumberFieldComparisonBetweenInput {
        private Double lower;
        private Double upper;

        public NumberFieldComparisonBetweenInput(Map<String, Object> args) {
            if (args != null) {
                this.lower = (Double) args.get("lower");
                this.upper = (Double) args.get("upper");
            }
        }

        public Double getLower() { return this.lower; }
        public Double getUpper() { return this.upper; }
        public void setLower(Double lower) { this.lower = lower; }
        public void setUpper(Double upper) { this.upper = upper; }
    }
    public static class CustomerSubscriptionFilterSubscriptionAddonFilterInput {
        private Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> and;
        private Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> or;
        private StringFieldComparisonInput id;
        private NumberFieldComparisonInput quantity;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput createdAt;

        public CustomerSubscriptionFilterSubscriptionAddonFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.quantity = new NumberFieldComparisonInput((Map<String, Object>) args.get("quantity"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
            }
        }

        public Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public NumberFieldComparisonInput getQuantity() { return this.quantity; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public void setAnd(Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterSubscriptionAddonFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setQuantity(NumberFieldComparisonInput quantity) { this.quantity = quantity; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
    }
    public static class CustomerSubscriptionFilterSubscriptionEntitlementFilterInput {
        private Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> and;
        private Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput subscriptionId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;

        public CustomerSubscriptionFilterSubscriptionEntitlementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerSubscriptionFilterSubscriptionEntitlementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class CustomerSubscriptionSortInput {
        private CustomerSubscriptionSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public CustomerSubscriptionSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof CustomerSubscriptionSortFields) {
                    this.field = (CustomerSubscriptionSortFields) args.get("field");
                } else {
                    this.field = CustomerSubscriptionSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public CustomerSubscriptionSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(CustomerSubscriptionSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum CustomerSubscriptionSortFields {
        environmentId,
        id,
        createdAt,
        startDate,
        endDate,
        cancellationDate,
        trialEndDate,
        effectiveEndDate,
        billingId,
        oldBillingId,
        crmId,
        crmLinkUrl,
        status,
        cancelReason,
        refId,
        subscriptionId,
        resourceId,
        pricingType,
        paymentCollection

    }

    public static class PromotionalEntitlementFilterInput {
        private Iterable<PromotionalEntitlementFilterInput> and;
        private Iterable<PromotionalEntitlementFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private PromotionalEntitlementStatusFilterComparisonInput status;
        private StringFieldComparisonInput environmentId;

        public PromotionalEntitlementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PromotionalEntitlementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PromotionalEntitlementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.status = new PromotionalEntitlementStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<PromotionalEntitlementFilterInput> getAnd() { return this.and; }
        public Iterable<PromotionalEntitlementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public PromotionalEntitlementStatusFilterComparisonInput getStatus() { return this.status; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<PromotionalEntitlementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PromotionalEntitlementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setStatus(PromotionalEntitlementStatusFilterComparisonInput status) { this.status = status; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class PromotionalEntitlementSortInput {
        private PromotionalEntitlementSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public PromotionalEntitlementSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof PromotionalEntitlementSortFields) {
                    this.field = (PromotionalEntitlementSortFields) args.get("field");
                } else {
                    this.field = PromotionalEntitlementSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public PromotionalEntitlementSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(PromotionalEntitlementSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum PromotionalEntitlementSortFields {
        id,
        createdAt,
        updatedAt,
        status,
        environmentId

    }








    /** Subscription scheduled schedule type */
    public enum SubscriptionScheduleType {
        Downgrade,
        BillingPeriod,
        UnitAmount,
        Addon,
        MigrateToLatest

    }

    /** Subscription scheduled schedule status */
    public enum SubscriptionScheduleStatus {
        PendingPayment,
        Scheduled,
        Canceled,
        Done,
        Failed

    }






    public static class CustomerResourceSubscriptionsArgs {
        private CustomerSubscriptionFilterInput filter;
        private Iterable<CustomerSubscriptionSortInput> sorting;

        public CustomerResourceSubscriptionsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new CustomerSubscriptionFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerSubscriptionSortInput>) args.get("sorting");
                }
            }
        }

        public CustomerSubscriptionFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerSubscriptionSortInput> getSorting() { return this.sorting; }
        public void setFilter(CustomerSubscriptionFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerSubscriptionSortInput> sorting) { this.sorting = sorting; }
    }

    /** SubscriptionInvoice status */
    public enum SubscriptionInvoiceStatus {
        OPEN,
        CANCELED,
        PAID

    }

    public static class CustomerSubscriptionAddonsArgs {
        private SubscriptionAddonFilterInput filter;
        private Iterable<SubscriptionAddonSortInput> sorting;

        public CustomerSubscriptionAddonsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new SubscriptionAddonFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<SubscriptionAddonSortInput>) args.get("sorting");
                }
            }
        }

        public SubscriptionAddonFilterInput getFilter() { return this.filter; }
        public Iterable<SubscriptionAddonSortInput> getSorting() { return this.sorting; }
        public void setFilter(SubscriptionAddonFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<SubscriptionAddonSortInput> sorting) { this.sorting = sorting; }
    }
    public static class CustomerSubscriptionSubscriptionEntitlementsArgs {
        private SubscriptionEntitlementFilterInput filter;
        private Iterable<SubscriptionEntitlementSortInput> sorting;

        public CustomerSubscriptionSubscriptionEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new SubscriptionEntitlementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<SubscriptionEntitlementSortInput>) args.get("sorting");
                }
            }
        }

        public SubscriptionEntitlementFilterInput getFilter() { return this.filter; }
        public Iterable<SubscriptionEntitlementSortInput> getSorting() { return this.sorting; }
        public void setFilter(SubscriptionEntitlementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<SubscriptionEntitlementSortInput> sorting) { this.sorting = sorting; }
    }
    public static class CustomerSubscriptionPricesArgs {
        private SubscriptionPriceFilterInput filter;
        private Iterable<SubscriptionPriceSortInput> sorting;

        public CustomerSubscriptionPricesArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new SubscriptionPriceFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<SubscriptionPriceSortInput>) args.get("sorting");
                }
            }
        }

        public SubscriptionPriceFilterInput getFilter() { return this.filter; }
        public Iterable<SubscriptionPriceSortInput> getSorting() { return this.sorting; }
        public void setFilter(SubscriptionPriceFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<SubscriptionPriceSortInput> sorting) { this.sorting = sorting; }
    }
    public static class SubscriptionAddonFilterInput {
        private Iterable<SubscriptionAddonFilterInput> and;
        private Iterable<SubscriptionAddonFilterInput> or;
        private StringFieldComparisonInput id;
        private NumberFieldComparisonInput quantity;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput createdAt;
        private SubscriptionAddonFilterPriceFilterInput price;
        private SubscriptionAddonFilterAddonFilterInput addon;
        private SubscriptionAddonFilterCustomerSubscriptionFilterInput subscription;

        public SubscriptionAddonFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionAddonFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionAddonFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.quantity = new NumberFieldComparisonInput((Map<String, Object>) args.get("quantity"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.price = new SubscriptionAddonFilterPriceFilterInput((Map<String, Object>) args.get("price"));
                this.addon = new SubscriptionAddonFilterAddonFilterInput((Map<String, Object>) args.get("addon"));
                this.subscription = new SubscriptionAddonFilterCustomerSubscriptionFilterInput((Map<String, Object>) args.get("subscription"));
            }
        }

        public Iterable<SubscriptionAddonFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionAddonFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public NumberFieldComparisonInput getQuantity() { return this.quantity; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public SubscriptionAddonFilterPriceFilterInput getPrice() { return this.price; }
        public SubscriptionAddonFilterAddonFilterInput getAddon() { return this.addon; }
        public SubscriptionAddonFilterCustomerSubscriptionFilterInput getSubscription() { return this.subscription; }
        public void setAnd(Iterable<SubscriptionAddonFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionAddonFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setQuantity(NumberFieldComparisonInput quantity) { this.quantity = quantity; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setPrice(SubscriptionAddonFilterPriceFilterInput price) { this.price = price; }
        public void setAddon(SubscriptionAddonFilterAddonFilterInput addon) { this.addon = addon; }
        public void setSubscription(SubscriptionAddonFilterCustomerSubscriptionFilterInput subscription) { this.subscription = subscription; }
    }
    public static class SubscriptionAddonFilterPriceFilterInput {
        private Iterable<SubscriptionAddonFilterPriceFilterInput> and;
        private Iterable<SubscriptionAddonFilterPriceFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private BillingPeriodFilterComparisonInput billingPeriod;
        private BillingModelFilterComparisonInput billingModel;
        private TiersModeFilterComparisonInput tiersMode;
        private StringFieldComparisonInput billingId;

        public SubscriptionAddonFilterPriceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionAddonFilterPriceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionAddonFilterPriceFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.billingPeriod = new BillingPeriodFilterComparisonInput((Map<String, Object>) args.get("billingPeriod"));
                this.billingModel = new BillingModelFilterComparisonInput((Map<String, Object>) args.get("billingModel"));
                this.tiersMode = new TiersModeFilterComparisonInput((Map<String, Object>) args.get("tiersMode"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
            }
        }

        public Iterable<SubscriptionAddonFilterPriceFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionAddonFilterPriceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public BillingPeriodFilterComparisonInput getBillingPeriod() { return this.billingPeriod; }
        public BillingModelFilterComparisonInput getBillingModel() { return this.billingModel; }
        public TiersModeFilterComparisonInput getTiersMode() { return this.tiersMode; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public void setAnd(Iterable<SubscriptionAddonFilterPriceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionAddonFilterPriceFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setBillingPeriod(BillingPeriodFilterComparisonInput billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingModel(BillingModelFilterComparisonInput billingModel) { this.billingModel = billingModel; }
        public void setTiersMode(TiersModeFilterComparisonInput tiersMode) { this.tiersMode = tiersMode; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
    }
    public static class SubscriptionAddonFilterAddonFilterInput {
        private Iterable<SubscriptionAddonFilterAddonFilterInput> and;
        private Iterable<SubscriptionAddonFilterAddonFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public SubscriptionAddonFilterAddonFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionAddonFilterAddonFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionAddonFilterAddonFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<SubscriptionAddonFilterAddonFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionAddonFilterAddonFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<SubscriptionAddonFilterAddonFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionAddonFilterAddonFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class SubscriptionAddonFilterCustomerSubscriptionFilterInput {
        private Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> and;
        private Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;

        public SubscriptionAddonFilterCustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
            }
        }

        public Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public void setAnd(Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionAddonFilterCustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
    }
    public static class SubscriptionAddonSortInput {
        private SubscriptionAddonSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public SubscriptionAddonSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof SubscriptionAddonSortFields) {
                    this.field = (SubscriptionAddonSortFields) args.get("field");
                } else {
                    this.field = SubscriptionAddonSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public SubscriptionAddonSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(SubscriptionAddonSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum SubscriptionAddonSortFields {
        id,
        quantity,
        updatedAt,
        createdAt

    }

    public static class SubscriptionEntitlementFilterInput {
        private Iterable<SubscriptionEntitlementFilterInput> and;
        private Iterable<SubscriptionEntitlementFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput subscriptionId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;
        private SubscriptionEntitlementFilterCustomerSubscriptionFilterInput subscription;
        private SubscriptionEntitlementFilterFeatureFilterInput feature;

        public SubscriptionEntitlementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionEntitlementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionEntitlementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.subscription = new SubscriptionEntitlementFilterCustomerSubscriptionFilterInput((Map<String, Object>) args.get("subscription"));
                this.feature = new SubscriptionEntitlementFilterFeatureFilterInput((Map<String, Object>) args.get("feature"));
            }
        }

        public Iterable<SubscriptionEntitlementFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionEntitlementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public SubscriptionEntitlementFilterCustomerSubscriptionFilterInput getSubscription() { return this.subscription; }
        public SubscriptionEntitlementFilterFeatureFilterInput getFeature() { return this.feature; }
        public void setAnd(Iterable<SubscriptionEntitlementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionEntitlementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setSubscription(SubscriptionEntitlementFilterCustomerSubscriptionFilterInput subscription) { this.subscription = subscription; }
        public void setFeature(SubscriptionEntitlementFilterFeatureFilterInput feature) { this.feature = feature; }
    }
    public static class SubscriptionEntitlementFilterCustomerSubscriptionFilterInput {
        private Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> and;
        private Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;

        public SubscriptionEntitlementFilterCustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
            }
        }

        public Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public void setAnd(Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionEntitlementFilterCustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
    }
    public static class SubscriptionEntitlementFilterFeatureFilterInput {
        private Iterable<SubscriptionEntitlementFilterFeatureFilterInput> and;
        private Iterable<SubscriptionEntitlementFilterFeatureFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput description;
        private FeatureTypeFilterComparisonInput featureType;
        private MeterTypeFilterComparisonInput meterType;
        private FeatureStatusFilterComparisonInput featureStatus;
        private StringFieldComparisonInput environmentId;

        public SubscriptionEntitlementFilterFeatureFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionEntitlementFilterFeatureFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionEntitlementFilterFeatureFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.featureType = new FeatureTypeFilterComparisonInput((Map<String, Object>) args.get("featureType"));
                this.meterType = new MeterTypeFilterComparisonInput((Map<String, Object>) args.get("meterType"));
                this.featureStatus = new FeatureStatusFilterComparisonInput((Map<String, Object>) args.get("featureStatus"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<SubscriptionEntitlementFilterFeatureFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionEntitlementFilterFeatureFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public FeatureTypeFilterComparisonInput getFeatureType() { return this.featureType; }
        public MeterTypeFilterComparisonInput getMeterType() { return this.meterType; }
        public FeatureStatusFilterComparisonInput getFeatureStatus() { return this.featureStatus; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<SubscriptionEntitlementFilterFeatureFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionEntitlementFilterFeatureFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setFeatureType(FeatureTypeFilterComparisonInput featureType) { this.featureType = featureType; }
        public void setMeterType(MeterTypeFilterComparisonInput meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatusFilterComparisonInput featureStatus) { this.featureStatus = featureStatus; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class FeatureTypeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private FeatureType eq;
        private FeatureType neq;
        private FeatureType gt;
        private FeatureType gte;
        private FeatureType lt;
        private FeatureType lte;
        private FeatureType like;
        private FeatureType notLike;
        private FeatureType iLike;
        private FeatureType notILike;
        private Iterable<FeatureType> in;
        private Iterable<FeatureType> notIn;

        public FeatureTypeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof FeatureType) {
                    this.eq = (FeatureType) args.get("eq");
                } else {
                    this.eq = FeatureType.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof FeatureType) {
                    this.neq = (FeatureType) args.get("neq");
                } else {
                    this.neq = FeatureType.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof FeatureType) {
                    this.gt = (FeatureType) args.get("gt");
                } else {
                    this.gt = FeatureType.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof FeatureType) {
                    this.gte = (FeatureType) args.get("gte");
                } else {
                    this.gte = FeatureType.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof FeatureType) {
                    this.lt = (FeatureType) args.get("lt");
                } else {
                    this.lt = FeatureType.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof FeatureType) {
                    this.lte = (FeatureType) args.get("lte");
                } else {
                    this.lte = FeatureType.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof FeatureType) {
                    this.like = (FeatureType) args.get("like");
                } else {
                    this.like = FeatureType.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof FeatureType) {
                    this.notLike = (FeatureType) args.get("notLike");
                } else {
                    this.notLike = FeatureType.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof FeatureType) {
                    this.iLike = (FeatureType) args.get("iLike");
                } else {
                    this.iLike = FeatureType.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof FeatureType) {
                    this.notILike = (FeatureType) args.get("notILike");
                } else {
                    this.notILike = FeatureType.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<FeatureType>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<FeatureType>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public FeatureType getEq() { return this.eq; }
        public FeatureType getNeq() { return this.neq; }
        public FeatureType getGt() { return this.gt; }
        public FeatureType getGte() { return this.gte; }
        public FeatureType getLt() { return this.lt; }
        public FeatureType getLte() { return this.lte; }
        public FeatureType getLike() { return this.like; }
        public FeatureType getNotLike() { return this.notLike; }
        public FeatureType getILike() { return this.iLike; }
        public FeatureType getNotILike() { return this.notILike; }
        public Iterable<FeatureType> getIn() { return this.in; }
        public Iterable<FeatureType> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(FeatureType eq) { this.eq = eq; }
        public void setNeq(FeatureType neq) { this.neq = neq; }
        public void setGt(FeatureType gt) { this.gt = gt; }
        public void setGte(FeatureType gte) { this.gte = gte; }
        public void setLt(FeatureType lt) { this.lt = lt; }
        public void setLte(FeatureType lte) { this.lte = lte; }
        public void setLike(FeatureType like) { this.like = like; }
        public void setNotLike(FeatureType notLike) { this.notLike = notLike; }
        public void setILike(FeatureType iLike) { this.iLike = iLike; }
        public void setNotILike(FeatureType notILike) { this.notILike = notILike; }
        public void setIn(Iterable<FeatureType> in) { this.in = in; }
        public void setNotIn(Iterable<FeatureType> notIn) { this.notIn = notIn; }
    }
    public static class MeterTypeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private MeterType eq;
        private MeterType neq;
        private MeterType gt;
        private MeterType gte;
        private MeterType lt;
        private MeterType lte;
        private MeterType like;
        private MeterType notLike;
        private MeterType iLike;
        private MeterType notILike;
        private Iterable<MeterType> in;
        private Iterable<MeterType> notIn;

        public MeterTypeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof MeterType) {
                    this.eq = (MeterType) args.get("eq");
                } else {
                    this.eq = MeterType.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof MeterType) {
                    this.neq = (MeterType) args.get("neq");
                } else {
                    this.neq = MeterType.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof MeterType) {
                    this.gt = (MeterType) args.get("gt");
                } else {
                    this.gt = MeterType.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof MeterType) {
                    this.gte = (MeterType) args.get("gte");
                } else {
                    this.gte = MeterType.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof MeterType) {
                    this.lt = (MeterType) args.get("lt");
                } else {
                    this.lt = MeterType.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof MeterType) {
                    this.lte = (MeterType) args.get("lte");
                } else {
                    this.lte = MeterType.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof MeterType) {
                    this.like = (MeterType) args.get("like");
                } else {
                    this.like = MeterType.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof MeterType) {
                    this.notLike = (MeterType) args.get("notLike");
                } else {
                    this.notLike = MeterType.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof MeterType) {
                    this.iLike = (MeterType) args.get("iLike");
                } else {
                    this.iLike = MeterType.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof MeterType) {
                    this.notILike = (MeterType) args.get("notILike");
                } else {
                    this.notILike = MeterType.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<MeterType>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<MeterType>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public MeterType getEq() { return this.eq; }
        public MeterType getNeq() { return this.neq; }
        public MeterType getGt() { return this.gt; }
        public MeterType getGte() { return this.gte; }
        public MeterType getLt() { return this.lt; }
        public MeterType getLte() { return this.lte; }
        public MeterType getLike() { return this.like; }
        public MeterType getNotLike() { return this.notLike; }
        public MeterType getILike() { return this.iLike; }
        public MeterType getNotILike() { return this.notILike; }
        public Iterable<MeterType> getIn() { return this.in; }
        public Iterable<MeterType> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(MeterType eq) { this.eq = eq; }
        public void setNeq(MeterType neq) { this.neq = neq; }
        public void setGt(MeterType gt) { this.gt = gt; }
        public void setGte(MeterType gte) { this.gte = gte; }
        public void setLt(MeterType lt) { this.lt = lt; }
        public void setLte(MeterType lte) { this.lte = lte; }
        public void setLike(MeterType like) { this.like = like; }
        public void setNotLike(MeterType notLike) { this.notLike = notLike; }
        public void setILike(MeterType iLike) { this.iLike = iLike; }
        public void setNotILike(MeterType notILike) { this.notILike = notILike; }
        public void setIn(Iterable<MeterType> in) { this.in = in; }
        public void setNotIn(Iterable<MeterType> notIn) { this.notIn = notIn; }
    }
    public static class FeatureStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private FeatureStatus eq;
        private FeatureStatus neq;
        private FeatureStatus gt;
        private FeatureStatus gte;
        private FeatureStatus lt;
        private FeatureStatus lte;
        private FeatureStatus like;
        private FeatureStatus notLike;
        private FeatureStatus iLike;
        private FeatureStatus notILike;
        private Iterable<FeatureStatus> in;
        private Iterable<FeatureStatus> notIn;

        public FeatureStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof FeatureStatus) {
                    this.eq = (FeatureStatus) args.get("eq");
                } else {
                    this.eq = FeatureStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof FeatureStatus) {
                    this.neq = (FeatureStatus) args.get("neq");
                } else {
                    this.neq = FeatureStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof FeatureStatus) {
                    this.gt = (FeatureStatus) args.get("gt");
                } else {
                    this.gt = FeatureStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof FeatureStatus) {
                    this.gte = (FeatureStatus) args.get("gte");
                } else {
                    this.gte = FeatureStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof FeatureStatus) {
                    this.lt = (FeatureStatus) args.get("lt");
                } else {
                    this.lt = FeatureStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof FeatureStatus) {
                    this.lte = (FeatureStatus) args.get("lte");
                } else {
                    this.lte = FeatureStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof FeatureStatus) {
                    this.like = (FeatureStatus) args.get("like");
                } else {
                    this.like = FeatureStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof FeatureStatus) {
                    this.notLike = (FeatureStatus) args.get("notLike");
                } else {
                    this.notLike = FeatureStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof FeatureStatus) {
                    this.iLike = (FeatureStatus) args.get("iLike");
                } else {
                    this.iLike = FeatureStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof FeatureStatus) {
                    this.notILike = (FeatureStatus) args.get("notILike");
                } else {
                    this.notILike = FeatureStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<FeatureStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<FeatureStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public FeatureStatus getEq() { return this.eq; }
        public FeatureStatus getNeq() { return this.neq; }
        public FeatureStatus getGt() { return this.gt; }
        public FeatureStatus getGte() { return this.gte; }
        public FeatureStatus getLt() { return this.lt; }
        public FeatureStatus getLte() { return this.lte; }
        public FeatureStatus getLike() { return this.like; }
        public FeatureStatus getNotLike() { return this.notLike; }
        public FeatureStatus getILike() { return this.iLike; }
        public FeatureStatus getNotILike() { return this.notILike; }
        public Iterable<FeatureStatus> getIn() { return this.in; }
        public Iterable<FeatureStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(FeatureStatus eq) { this.eq = eq; }
        public void setNeq(FeatureStatus neq) { this.neq = neq; }
        public void setGt(FeatureStatus gt) { this.gt = gt; }
        public void setGte(FeatureStatus gte) { this.gte = gte; }
        public void setLt(FeatureStatus lt) { this.lt = lt; }
        public void setLte(FeatureStatus lte) { this.lte = lte; }
        public void setLike(FeatureStatus like) { this.like = like; }
        public void setNotLike(FeatureStatus notLike) { this.notLike = notLike; }
        public void setILike(FeatureStatus iLike) { this.iLike = iLike; }
        public void setNotILike(FeatureStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<FeatureStatus> in) { this.in = in; }
        public void setNotIn(Iterable<FeatureStatus> notIn) { this.notIn = notIn; }
    }
    public static class SubscriptionEntitlementSortInput {
        private SubscriptionEntitlementSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public SubscriptionEntitlementSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof SubscriptionEntitlementSortFields) {
                    this.field = (SubscriptionEntitlementSortFields) args.get("field");
                } else {
                    this.field = SubscriptionEntitlementSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public SubscriptionEntitlementSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(SubscriptionEntitlementSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum SubscriptionEntitlementSortFields {
        id,
        subscriptionId,
        createdAt,
        updatedAt,
        environmentId

    }

    public static class SubscriptionPriceFilterInput {
        private Iterable<SubscriptionPriceFilterInput> and;
        private Iterable<SubscriptionPriceFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private NumberFieldComparisonInput usageLimit;
        private StringFieldComparisonInput featureId;
        private BillingModelFilterComparisonInput billingModel;
        private SubscriptionPriceFilterPriceFilterInput price;
        private SubscriptionPriceFilterCustomerSubscriptionFilterInput subscription;

        public SubscriptionPriceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionPriceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionPriceFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.usageLimit = new NumberFieldComparisonInput((Map<String, Object>) args.get("usageLimit"));
                this.featureId = new StringFieldComparisonInput((Map<String, Object>) args.get("featureId"));
                this.billingModel = new BillingModelFilterComparisonInput((Map<String, Object>) args.get("billingModel"));
                this.price = new SubscriptionPriceFilterPriceFilterInput((Map<String, Object>) args.get("price"));
                this.subscription = new SubscriptionPriceFilterCustomerSubscriptionFilterInput((Map<String, Object>) args.get("subscription"));
            }
        }

        public Iterable<SubscriptionPriceFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionPriceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public NumberFieldComparisonInput getUsageLimit() { return this.usageLimit; }
        public StringFieldComparisonInput getFeatureId() { return this.featureId; }
        public BillingModelFilterComparisonInput getBillingModel() { return this.billingModel; }
        public SubscriptionPriceFilterPriceFilterInput getPrice() { return this.price; }
        public SubscriptionPriceFilterCustomerSubscriptionFilterInput getSubscription() { return this.subscription; }
        public void setAnd(Iterable<SubscriptionPriceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionPriceFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setUsageLimit(NumberFieldComparisonInput usageLimit) { this.usageLimit = usageLimit; }
        public void setFeatureId(StringFieldComparisonInput featureId) { this.featureId = featureId; }
        public void setBillingModel(BillingModelFilterComparisonInput billingModel) { this.billingModel = billingModel; }
        public void setPrice(SubscriptionPriceFilterPriceFilterInput price) { this.price = price; }
        public void setSubscription(SubscriptionPriceFilterCustomerSubscriptionFilterInput subscription) { this.subscription = subscription; }
    }
    public static class SubscriptionPriceFilterPriceFilterInput {
        private Iterable<SubscriptionPriceFilterPriceFilterInput> and;
        private Iterable<SubscriptionPriceFilterPriceFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private BillingPeriodFilterComparisonInput billingPeriod;
        private BillingModelFilterComparisonInput billingModel;
        private TiersModeFilterComparisonInput tiersMode;
        private StringFieldComparisonInput billingId;

        public SubscriptionPriceFilterPriceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionPriceFilterPriceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionPriceFilterPriceFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.billingPeriod = new BillingPeriodFilterComparisonInput((Map<String, Object>) args.get("billingPeriod"));
                this.billingModel = new BillingModelFilterComparisonInput((Map<String, Object>) args.get("billingModel"));
                this.tiersMode = new TiersModeFilterComparisonInput((Map<String, Object>) args.get("tiersMode"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
            }
        }

        public Iterable<SubscriptionPriceFilterPriceFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionPriceFilterPriceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public BillingPeriodFilterComparisonInput getBillingPeriod() { return this.billingPeriod; }
        public BillingModelFilterComparisonInput getBillingModel() { return this.billingModel; }
        public TiersModeFilterComparisonInput getTiersMode() { return this.tiersMode; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public void setAnd(Iterable<SubscriptionPriceFilterPriceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionPriceFilterPriceFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setBillingPeriod(BillingPeriodFilterComparisonInput billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingModel(BillingModelFilterComparisonInput billingModel) { this.billingModel = billingModel; }
        public void setTiersMode(TiersModeFilterComparisonInput tiersMode) { this.tiersMode = tiersMode; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
    }
    public static class SubscriptionPriceFilterCustomerSubscriptionFilterInput {
        private Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> and;
        private Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;

        public SubscriptionPriceFilterCustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
            }
        }

        public Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public void setAnd(Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionPriceFilterCustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
    }
    public static class SubscriptionPriceSortInput {
        private SubscriptionPriceSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public SubscriptionPriceSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof SubscriptionPriceSortFields) {
                    this.field = (SubscriptionPriceSortFields) args.get("field");
                } else {
                    this.field = SubscriptionPriceSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public SubscriptionPriceSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(SubscriptionPriceSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum SubscriptionPriceSortFields {
        id,
        createdAt,
        updatedAt,
        usageLimit,
        featureId,
        billingModel

    }





    /** DenyReason of get access policy */
    public enum AccessDeniedReason {
        FeatureNotFound,
        CustomerNotFound,
        CustomerIsArchived,
        CustomerResourceNotFound,
        NoActiveSubscription,
        NoFeatureEntitlementInSubscription,
        RequestedUsageExceedingLimit,
        Unknown

    }































    /** The type of the discount */
    public enum DiscountType {
        FIXED,
        PERCENTAGE

    }

    /** The type of the discount duration */
    public enum DiscountDurationType {
        FOREVER,
        REPEATING,
        ONCE

    }






    /** Provision subscription status */
    public enum ProvisionSubscriptionStatus {
        PAYMENT_REQUIRED,
        SUCCESS

    }
















    /** Member Status. */
    public enum MemberStatus {
        INVITED,
        REGISTERED

    }


    public enum Department {
        ENGINEERING,
        PRODUCT,
        GROWTH,
        MARKETING,
        MONETIZATION,
        CEO_OR_FOUNDER,
        OTHER

    }























    public static class SubscriptionMigrationTaskPackagesArgs {
        private PackageDtoFilterInput filter;
        private Iterable<PackageDtoSortInput> sorting;

        public SubscriptionMigrationTaskPackagesArgs(Map<String, Object> args) {
            if (args != null) {
                this.filter = new PackageDtoFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PackageDtoSortInput>) args.get("sorting");
                }
            }
        }

        public PackageDtoFilterInput getFilter() { return this.filter; }
        public Iterable<PackageDtoSortInput> getSorting() { return this.sorting; }
        public void setFilter(PackageDtoFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PackageDtoSortInput> sorting) { this.sorting = sorting; }
    }
    public enum TaskType {
        SUBSCRIPTION_MIGRATION,
        RESYNC_INTEGRATION,
        IMPORT_INTEGRATION_CATALOG,
        IMPORT_INTEGRATION_CUSTOMERS,
        RECALCULATE_ENTITLEMENTS,
        IMPORT_SUBSCRIPTIONS_BULK

    }

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        PARTIALLY_FAILED,
        FAILED

    }

    public static class PackageDtoFilterInput {
        private Iterable<PackageDtoFilterInput> and;
        private Iterable<PackageDtoFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public PackageDtoFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PackageDtoFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PackageDtoFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<PackageDtoFilterInput> getAnd() { return this.and; }
        public Iterable<PackageDtoFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<PackageDtoFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PackageDtoFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class PackageDtoSortInput {
        private PackageDtoSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public PackageDtoSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof PackageDtoSortFields) {
                    this.field = (PackageDtoSortFields) args.get("field");
                } else {
                    this.field = PackageDtoSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public PackageDtoSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(PackageDtoSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum PackageDtoSortFields {
        id,
        createdAt,
        updatedAt,
        refId,
        billingId,
        displayName,
        status,
        pricingType,
        description,
        environmentId,
        productId,
        isLatest,
        versionNumber

    }



































































































    /** Alignment */
    public enum Alignment {
        LEFT,
        CENTER,
        RIGHT

    }


    /** Font weight */
    public enum FontWeight {
        NORMAL,
        BOLD

    }













































    /** Subscription decision strategy */
    public enum SubscriptionDecisionStrategy {
        PREDEFINED_FREE_PLAN,
        PREDEFINED_TRIAL_PLAN,
        REQUESTED_PLAN,
        SKIPPED_SUBSCRIPTION_CREATION

    }










































    /** HookStatus. */
    public enum HookStatus {
        INACTIVE,
        ACTIVE

    }

    /** EventLogType */
    public enum EventLogType {
        CUSTOMER_CREATED,
        CUSTOMER_UPDATED,
        CUSTOMER_DELETED,
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_TRIAL_STARTED,
        SUBSCRIPTION_TRIAL_EXPIRED,
        SUBSCRIPTION_TRIAL_CONVERTED,
        SUBSCRIPTION_TRIAL_ENDS_SOON,
        SUBSCRIPTION_UPDATED,
        SUBSCRIPTION_CANCELED,
        SUBSCRIPTION_EXPIRED,
        SUBSCRIPTION_USAGE_UPDATED,
        CREATE_SUBSCRIPTION_FAILED,
        PLAN_CREATED,
        PLAN_UPDATED,
        PLAN_DELETED,
        ADDON_CREATED,
        ADDON_UPDATED,
        ADDON_DELETED,
        FEATURE_CREATED,
        FEATURE_UPDATED,
        FEATURE_DELETED,
        ENTITLEMENT_REQUESTED,
        ENTITLEMENT_GRANTED,
        ENTITLEMENT_DENIED,
        ENTITLEMENTS_UPDATED,
        MEASUREMENT_REPORTED,
        PROMOTIONAL_ENTITLEMENT_GRANTED,
        PROMOTIONAL_ENTITLEMENT_UPDATED,
        PROMOTIONAL_ENTITLEMENT_EXPIRED,
        PROMOTIONAL_ENTITLEMENT_REVOKED,
        PACKAGE_PUBLISHED,
        RESYNC_INTEGRATION_TRIGGERED,
        COUPON_CREATED,
        COUPON_UPDATED,
        COUPON_ARCHIVED,
        IMPORT_INTEGRATION_CATALOG_TRIGGERED,
        IMPORT_INTEGRATION_CUSTOMERS_TRIGGERED,
        SYNC_FAILED,
        CUSTOMER_PAYMENT_FAILED,
        PRODUCT_CREATED,
        PRODUCT_UPDATED,
        PRODUCT_DELETED,
        ENVIRONMENT_DELETED,
        WIDGET_CONFIGURATION_UPDATED,
        EDGE_API_DATA_RESYNC,
        CUSTOMER_RESOURCE_ENTITLEMENT_CALCULATION_TRIGGERED,
        CUSTOMER_ENTITLEMENT_CALCULATION_TRIGGERED,
        RECALCULATE_ENTITLEMENTS_TRIGGERED,
        IMPORT_SUBSCRIPTIONS_BULK_TRIGGERED

    }






















    /** Billing vendor identifiers */
    public enum BillingVendorIdentifier {
        STRIPE

    }


    /** error codes */
    public enum ErrorCode {
        RateLimitExceeded,
        BadUserInput,
        Unauthenticated,
        CustomerNotFound,
        TooManySubscriptionsPerCustomer,
        CustomerResourceNotFound,
        FeatureNotFound,
        DuplicatedEntityNotAllowed,
        EntityIsArchivedError,
        IntegrityViolation,
        MemberNotFound,
        PlanNotFound,
        PromotionalEntitlementNotFoundError,
        SubscriptionMustHaveSinglePlanError,
        AddonNotFound,
        ScheduledMigrationAlreadyExistsError,
        SubscriptionAlreadyOnLatestPlanError,
        EnvironmentMissing,
        EntityIdDifferentFromRefIdError,
        UnsupportedFeatureType,
        UnsupportedVendorIdentifier,
        UnsupportedSubscriptionScheduleType,
        InvalidEntitlementResetPeriod,
        UncompatibleSubscriptionAddon,
        UnPublishedPackage,
        MeteringNotAvailableForFeatureType,
        IdentityForbidden,
        AuthCustomerMismatch,
        FetchAllCountriesPricesNotAllowed,
        MemberInvitationError,
        UnexpectedError,
        PlanAlreadyExtended,
        PlansCircularDependencyError,
        NoFeatureEntitlementInSubscription,
        CheckoutIsNotSupported,
        PriceNotFound,
        InvalidMemberDelete,
        PackageAlreadyPublished,
        SubscriptionNotFound,
        DraftPlanCantBeArchived,
        PlanWithChildCantBeDeleted,
        PlanCannotBePublishWhenBasePlanIsDraft,
        PlanIsUsedAsDefaultStartPlan,
        PlanIsUsedAsDowngradePlan,
        InvalidAddressError,
        InvalidQuantity,
        BillingPeriodMissingError,
        DowngradeBillingPeriodNotSupportedError,
        CustomerHasNoPaymentMethod,
        CustomerAlreadyUsesCoupon,
        CustomerAlreadyHaveCustomerCoupon,
        CheckoutOptionsMissing,
        SubscriptionAlreadyCanceledOrExpired,
        TrialMustBeCancelledImmediately,
        InvalidCancellationDate,
        FailedToImportCustomer,
        PackagePricingTypeNotSet,
        TrialMinDateError,
        InvalidSubscriptionStatus,
        InvalidArgumentError,
        EditAllowedOnDraftPackageOnlyError,
        IntegrationNotFound,
        ResyncAlreadyInProgress,
        CouponNotFound,
        ArchivedCouponCantBeApplied,
        ImportAlreadyInProgress,
        CustomerNoBillingId,
        StripeCustomerIsDeleted,
        InitStripePaymentMethodError,
        AddonHasToHavePriceError,
        SelectedBillingModelDoesntMatchImportedItemError,
        CannotDeleteProductError,
        CannotDeleteCustomerError,
        CannotDeleteFeatureError,
        InvalidUpdatePriceUnitAmountError,
        AccountNotFoundError,
        ExperimentNotFoundError,
        ExperimentAlreadyRunning,
        ExperimentStatusError,
        OperationNotAllowedDuringInProgressExperiment,
        EntitlementsMustBelongToSamePackage,
        MeterMustBeAssociatedToMeteredFeature,
        CannotEditPackageInNonDraftMode,
        MissingEntityIdError,
        NoProductsAvailable,
        PromotionCodeNotFound,
        PromotionCodeNotForCustomer,
        PromotionCodeNotActive,
        PromotionCodeMaxRedemptionsReached,
        PromotionCodeMinimumAmountNotReached,
        PromotionCodeCustomerNotFirstPurchase,
        FailedToCreateCheckoutSessionError,
        AddonWithDraftCannotBeDeletedError,
        PaymentMethodNotFoundError,
        StripeError,
        CannotReportUsageForEntitlementWithMeterError,
        RecalculateEntitlementsError,
        ImportSubscriptionsBulkError,
        InvalidMetadataError,
        CannotUpsertToPackageThatHasDraft

    }

    public enum VendorIdentifier {
        ZUORA,
        STRIPE,
        HUBSPOT

    }

    public enum SourceType {
        JS_CLIENT_SDK,
        NODE_SERVER_SDK,
        PERSISTENT_CACHE_SERVICE

    }

    public static class QueryMembersArgs {
        private CursorPagingInput paging;
        private MemberFilterInput filter;
        private Iterable<MemberSortInput> sorting;

        public QueryMembersArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new MemberFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<MemberSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public MemberFilterInput getFilter() { return this.filter; }
        public Iterable<MemberSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(MemberFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<MemberSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryEntitlementArgs {
        private FetchEntitlementQueryInput query;

        public QueryEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.query = new FetchEntitlementQueryInput((Map<String, Object>) args.get("query"));
            }
        }

        public FetchEntitlementQueryInput getQuery() { return this.query; }
        public void setQuery(FetchEntitlementQueryInput query) { this.query = query; }
    }
    public static class QueryCachedEntitlementsArgs {
        private FetchEntitlementsQueryInput query;

        public QueryCachedEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.query = new FetchEntitlementsQueryInput((Map<String, Object>) args.get("query"));
            }
        }

        public FetchEntitlementsQueryInput getQuery() { return this.query; }
        public void setQuery(FetchEntitlementsQueryInput query) { this.query = query; }
    }
    public static class QueryEntitlementsArgs {
        private FetchEntitlementsQueryInput query;

        public QueryEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.query = new FetchEntitlementsQueryInput((Map<String, Object>) args.get("query"));
            }
        }

        public FetchEntitlementsQueryInput getQuery() { return this.query; }
        public void setQuery(FetchEntitlementsQueryInput query) { this.query = query; }
    }
    public static class QuerySubscriptionMigrationTasksArgs {
        private CursorPagingInput paging;
        private SubscriptionMigrationTaskFilterInput filter;
        private Iterable<SubscriptionMigrationTaskSortInput> sorting;

        public QuerySubscriptionMigrationTasksArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new SubscriptionMigrationTaskFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<SubscriptionMigrationTaskSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public SubscriptionMigrationTaskFilterInput getFilter() { return this.filter; }
        public Iterable<SubscriptionMigrationTaskSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(SubscriptionMigrationTaskFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<SubscriptionMigrationTaskSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryImportIntegrationTasksArgs {
        private CursorPagingInput paging;
        private ImportIntegrationTaskFilterInput filter;
        private Iterable<ImportIntegrationTaskSortInput> sorting;

        public QueryImportIntegrationTasksArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new ImportIntegrationTaskFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<ImportIntegrationTaskSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public ImportIntegrationTaskFilterInput getFilter() { return this.filter; }
        public Iterable<ImportIntegrationTaskSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(ImportIntegrationTaskFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<ImportIntegrationTaskSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryStripeProductsArgs {
        private StripeProductSearchInput input;

        public QueryStripeProductsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new StripeProductSearchInput((Map<String, Object>) args.get("input"));
            }
        }

        public StripeProductSearchInput getInput() { return this.input; }
        public void setInput(StripeProductSearchInput input) { this.input = input; }
    }
    public static class QueryStripeCustomersArgs {
        private StripeCustomerSearchInput input;

        public QueryStripeCustomersArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new StripeCustomerSearchInput((Map<String, Object>) args.get("input"));
            }
        }

        public StripeCustomerSearchInput getInput() { return this.input; }
        public void setInput(StripeCustomerSearchInput input) { this.input = input; }
    }
    public static class QueryStripeSubscriptionsArgs {
        private StripeSubscriptionSearchInput input;

        public QueryStripeSubscriptionsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new StripeSubscriptionSearchInput((Map<String, Object>) args.get("input"));
            }
        }

        public StripeSubscriptionSearchInput getInput() { return this.input; }
        public void setInput(StripeSubscriptionSearchInput input) { this.input = input; }
    }
    public static class QueryProductsArgs {
        private CursorPagingInput paging;
        private ProductFilterInput filter;
        private Iterable<ProductSortInput> sorting;

        public QueryProductsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new ProductFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<ProductSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public ProductFilterInput getFilter() { return this.filter; }
        public Iterable<ProductSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(ProductFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<ProductSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryEnvironmentsArgs {
        private CursorPagingInput paging;
        private EnvironmentFilterInput filter;
        private Iterable<EnvironmentSortInput> sorting;

        public QueryEnvironmentsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new EnvironmentFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<EnvironmentSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public EnvironmentFilterInput getFilter() { return this.filter; }
        public Iterable<EnvironmentSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(EnvironmentFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<EnvironmentSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryGetExperimentStatsArgs {
        private ExperimentStatsQueryInput query;

        public QueryGetExperimentStatsArgs(Map<String, Object> args) {
            if (args != null) {
                this.query = new ExperimentStatsQueryInput((Map<String, Object>) args.get("query"));
            }
        }

        public ExperimentStatsQueryInput getQuery() { return this.query; }
        public void setQuery(ExperimentStatsQueryInput query) { this.query = query; }
    }
    public static class QueryExperimentArgs {
        private String id;

        public QueryExperimentArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class QueryExperimentsArgs {
        private CursorPagingInput paging;
        private ExperimentFilterInput filter;
        private Iterable<ExperimentSortInput> sorting;

        public QueryExperimentsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new ExperimentFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<ExperimentSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public ExperimentFilterInput getFilter() { return this.filter; }
        public Iterable<ExperimentSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(ExperimentFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<ExperimentSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryCouponArgs {
        private String id;

        public QueryCouponArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class QueryCouponsArgs {
        private CursorPagingInput paging;
        private CouponFilterInput filter;
        private Iterable<CouponSortInput> sorting;

        public QueryCouponsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new CouponFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CouponSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public CouponFilterInput getFilter() { return this.filter; }
        public Iterable<CouponSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(CouponFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CouponSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryIntegrationsArgs {
        private CursorPagingInput paging;
        private IntegrationFilterInput filter;
        private Iterable<IntegrationSortInput> sorting;

        public QueryIntegrationsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new IntegrationFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<IntegrationSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public IntegrationFilterInput getFilter() { return this.filter; }
        public Iterable<IntegrationSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(IntegrationFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<IntegrationSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryGetActiveSubscriptionsArgs {
        private GetActiveSubscriptionsInput input;

        public QueryGetActiveSubscriptionsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetActiveSubscriptionsInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetActiveSubscriptionsInput getInput() { return this.input; }
        public void setInput(GetActiveSubscriptionsInput input) { this.input = input; }
    }
    public static class QueryCustomerSubscriptionsArgs {
        private CursorPagingInput paging;
        private CustomerSubscriptionFilterInput filter;
        private Iterable<CustomerSubscriptionSortInput> sorting;

        public QueryCustomerSubscriptionsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new CustomerSubscriptionFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerSubscriptionSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public CustomerSubscriptionFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerSubscriptionSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(CustomerSubscriptionFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerSubscriptionSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryFeaturesArgs {
        private CursorPagingInput paging;
        private FeatureFilterInput filter;
        private Iterable<FeatureSortInput> sorting;

        public QueryFeaturesArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new FeatureFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<FeatureSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public FeatureFilterInput getFilter() { return this.filter; }
        public Iterable<FeatureSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(FeatureFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<FeatureSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryPackageEntitlementsArgs {
        private CursorPagingInput paging;
        private PackageEntitlementFilterInput filter;
        private Iterable<PackageEntitlementSortInput> sorting;

        public QueryPackageEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new PackageEntitlementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PackageEntitlementSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public PackageEntitlementFilterInput getFilter() { return this.filter; }
        public Iterable<PackageEntitlementSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(PackageEntitlementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PackageEntitlementSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryPromotionalEntitlementsArgs {
        private CursorPagingInput paging;
        private PromotionalEntitlementFilterInput filter;
        private Iterable<PromotionalEntitlementSortInput> sorting;

        public QueryPromotionalEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new PromotionalEntitlementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PromotionalEntitlementSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public PromotionalEntitlementFilterInput getFilter() { return this.filter; }
        public Iterable<PromotionalEntitlementSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(PromotionalEntitlementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PromotionalEntitlementSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QuerySubscriptionEntitlementsArgs {
        private CursorPagingInput paging;
        private SubscriptionEntitlementFilterInput filter;
        private Iterable<SubscriptionEntitlementSortInput> sorting;

        public QuerySubscriptionEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new SubscriptionEntitlementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<SubscriptionEntitlementSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public SubscriptionEntitlementFilterInput getFilter() { return this.filter; }
        public Iterable<SubscriptionEntitlementSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(SubscriptionEntitlementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<SubscriptionEntitlementSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryCustomerPortalArgs {
        private CustomerPortalInput input;

        public QueryCustomerPortalArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CustomerPortalInput((Map<String, Object>) args.get("input"));
            }
        }

        public CustomerPortalInput getInput() { return this.input; }
        public void setInput(CustomerPortalInput input) { this.input = input; }
    }
    public static class QueryGetCustomerByRefIdArgs {
        private GetCustomerByRefIdInput input;

        public QueryGetCustomerByRefIdArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetCustomerByRefIdInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetCustomerByRefIdInput getInput() { return this.input; }
        public void setInput(GetCustomerByRefIdInput input) { this.input = input; }
    }
    public static class QueryWidgetConfigurationArgs {
        private GetWidgetConfigurationInput input;

        public QueryWidgetConfigurationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetWidgetConfigurationInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetWidgetConfigurationInput getInput() { return this.input; }
        public void setInput(GetWidgetConfigurationInput input) { this.input = input; }
    }
    public static class QueryCustomersArgs {
        private CursorPagingInput paging;
        private CustomerFilterInput filter;
        private Iterable<CustomerSortInput> sorting;

        public QueryCustomersArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new CustomerFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public CustomerFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(CustomerFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryCustomerResourcesArgs {
        private CursorPagingInput paging;
        private CustomerResourceFilterInput filter;
        private Iterable<CustomerResourceSortInput> sorting;

        public QueryCustomerResourcesArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new CustomerResourceFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<CustomerResourceSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public CustomerResourceFilterInput getFilter() { return this.filter; }
        public Iterable<CustomerResourceSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(CustomerResourceFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<CustomerResourceSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryGetPaywallArgs {
        private GetPaywallInput input;

        public QueryGetPaywallArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetPaywallInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetPaywallInput getInput() { return this.input; }
        public void setInput(GetPaywallInput input) { this.input = input; }
    }
    public static class QueryPaywallArgs {
        private GetPaywallInput input;

        public QueryPaywallArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetPaywallInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetPaywallInput getInput() { return this.input; }
        public void setInput(GetPaywallInput input) { this.input = input; }
    }
    public static class QueryMockPaywallArgs {
        private GetPaywallInput input;

        public QueryMockPaywallArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetPaywallInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetPaywallInput getInput() { return this.input; }
        public void setInput(GetPaywallInput input) { this.input = input; }
    }
    public static class QueryGetPlanByRefIdArgs {
        private GetPackageByRefIdInput input;

        public QueryGetPlanByRefIdArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetPackageByRefIdInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetPackageByRefIdInput getInput() { return this.input; }
        public void setInput(GetPackageByRefIdInput input) { this.input = input; }
    }
    public static class QueryGetAddonByRefIdArgs {
        private GetPackageByRefIdInput input;

        public QueryGetAddonByRefIdArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GetPackageByRefIdInput((Map<String, Object>) args.get("input"));
            }
        }

        public GetPackageByRefIdInput getInput() { return this.input; }
        public void setInput(GetPackageByRefIdInput input) { this.input = input; }
    }
    public static class QueryPlansArgs {
        private CursorPagingInput paging;
        private PlanFilterInput filter;
        private Iterable<PlanSortInput> sorting;

        public QueryPlansArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new PlanFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<PlanSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public PlanFilterInput getFilter() { return this.filter; }
        public Iterable<PlanSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(PlanFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<PlanSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryAddonsArgs {
        private CursorPagingInput paging;
        private AddonFilterInput filter;
        private Iterable<AddonSortInput> sorting;

        public QueryAddonsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new AddonFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<AddonSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public AddonFilterInput getFilter() { return this.filter; }
        public Iterable<AddonSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(AddonFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<AddonSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryUsageHistoryArgs {
        private UsageHistoryInput usageHistoryInput;

        public QueryUsageHistoryArgs(Map<String, Object> args) {
            if (args != null) {
                this.usageHistoryInput = new UsageHistoryInput((Map<String, Object>) args.get("usageHistoryInput"));
            }
        }

        public UsageHistoryInput getUsageHistoryInput() { return this.usageHistoryInput; }
        public void setUsageHistoryInput(UsageHistoryInput usageHistoryInput) { this.usageHistoryInput = usageHistoryInput; }
    }
    public static class QueryUsageEventsArgs {
        private UsageEventsInput input;

        public QueryUsageEventsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UsageEventsInput((Map<String, Object>) args.get("input"));
            }
        }

        public UsageEventsInput getInput() { return this.input; }
        public void setInput(UsageEventsInput input) { this.input = input; }
    }
    public static class QueryAggregatedEventsByCustomerArgs {
        private AggregatedEventsByCustomerInput input;

        public QueryAggregatedEventsByCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new AggregatedEventsByCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public AggregatedEventsByCustomerInput getInput() { return this.input; }
        public void setInput(AggregatedEventsByCustomerInput input) { this.input = input; }
    }
    public static class QueryEventsFieldsArgs {
        private EventsFieldsInput input;

        public QueryEventsFieldsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new EventsFieldsInput((Map<String, Object>) args.get("input"));
            }
        }

        public EventsFieldsInput getInput() { return this.input; }
        public void setInput(EventsFieldsInput input) { this.input = input; }
    }
    public static class QueryUsageMeasurementsArgs {
        private CursorPagingInput paging;
        private UsageMeasurementFilterInput filter;
        private Iterable<UsageMeasurementSortInput> sorting;

        public QueryUsageMeasurementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new UsageMeasurementFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<UsageMeasurementSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public UsageMeasurementFilterInput getFilter() { return this.filter; }
        public Iterable<UsageMeasurementSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(UsageMeasurementFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<UsageMeasurementSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryTestHookDataArgs {
        private EventLogType eventLogType;

        public QueryTestHookDataArgs(Map<String, Object> args) {
            if (args != null) {
                if (args.get("eventLogType") instanceof EventLogType) {
                    this.eventLogType = (EventLogType) args.get("eventLogType");
                } else {
                    this.eventLogType = EventLogType.valueOf((String) args.get("eventLogType"));
                }
            }
        }

        public EventLogType getEventLogType() { return this.eventLogType; }
        public void setEventLogType(EventLogType eventLogType) { this.eventLogType = eventLogType; }
    }
    public static class QuerySendTestHookArgs {
        private TestHookInput testHookInput;

        public QuerySendTestHookArgs(Map<String, Object> args) {
            if (args != null) {
                this.testHookInput = new TestHookInput((Map<String, Object>) args.get("testHookInput"));
            }
        }

        public TestHookInput getTestHookInput() { return this.testHookInput; }
        public void setTestHookInput(TestHookInput testHookInput) { this.testHookInput = testHookInput; }
    }
    public static class QueryHookArgs {
        private String id;

        public QueryHookArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class QueryHooksArgs {
        private CursorPagingInput paging;
        private HookFilterInput filter;
        private Iterable<HookSortInput> sorting;

        public QueryHooksArgs(Map<String, Object> args) {
            if (args != null) {
                this.paging = new CursorPagingInput((Map<String, Object>) args.get("paging"));
                this.filter = new HookFilterInput((Map<String, Object>) args.get("filter"));
                if (args.get("sorting") != null) {
                    this.sorting = (Iterable<HookSortInput>) args.get("sorting");
                }
            }
        }

        public CursorPagingInput getPaging() { return this.paging; }
        public HookFilterInput getFilter() { return this.filter; }
        public Iterable<HookSortInput> getSorting() { return this.sorting; }
        public void setPaging(CursorPagingInput paging) { this.paging = paging; }
        public void setFilter(HookFilterInput filter) { this.filter = filter; }
        public void setSorting(Iterable<HookSortInput> sorting) { this.sorting = sorting; }
    }
    public static class QueryCheckoutStateArgs {
        private CheckoutStateInput input;

        public QueryCheckoutStateArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CheckoutStateInput((Map<String, Object>) args.get("input"));
            }
        }

        public CheckoutStateInput getInput() { return this.input; }
        public void setInput(CheckoutStateInput input) { this.input = input; }
    }
    public static class CursorPagingInput {
        private Object before;
        private Object after;
        private Integer first;
        private Integer last;

        public CursorPagingInput(Map<String, Object> args) {
            if (args != null) {
                this.before = (Object) args.get("before");
                this.after = (Object) args.get("after");
                this.first = (Integer) args.get("first");
                this.last = (Integer) args.get("last");
            }
        }

        public Object getBefore() { return this.before; }
        public Object getAfter() { return this.after; }
        public Integer getFirst() { return this.first; }
        public Integer getLast() { return this.last; }
        public void setBefore(Object before) { this.before = before; }
        public void setAfter(Object after) { this.after = after; }
        public void setFirst(Integer first) { this.first = first; }
        public void setLast(Integer last) { this.last = last; }
    }
    public static class MemberFilterInput {
        private Iterable<MemberFilterInput> and;
        private Iterable<MemberFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;

        public MemberFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<MemberFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<MemberFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
            }
        }

        public Iterable<MemberFilterInput> getAnd() { return this.and; }
        public Iterable<MemberFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public void setAnd(Iterable<MemberFilterInput> and) { this.and = and; }
        public void setOr(Iterable<MemberFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
    }
    public static class MemberSortInput {
        private MemberSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public MemberSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof MemberSortFields) {
                    this.field = (MemberSortFields) args.get("field");
                } else {
                    this.field = MemberSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public MemberSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(MemberSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum MemberSortFields {
        id,
        createdAt

    }

    public static class FetchEntitlementQueryInput {
        private String customerId;
        private String resourceId;
        private String featureId;
        private EntitlementOptionsInput options;
        private String environmentId;

        public FetchEntitlementQueryInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.featureId = (String) args.get("featureId");
                this.options = new EntitlementOptionsInput((Map<String, Object>) args.get("options"));
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getFeatureId() { return this.featureId; }
        public EntitlementOptionsInput getOptions() { return this.options; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setOptions(EntitlementOptionsInput options) { this.options = options; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class EntitlementOptionsInput {
        private Boolean shouldTrack;
        private Double requestedUsage;

        public EntitlementOptionsInput(Map<String, Object> args) {
            if (args != null) {
                this.shouldTrack = (Boolean) args.get("shouldTrack");
                this.requestedUsage = (Double) args.get("requestedUsage");
            }
        }

        public Boolean getShouldTrack() { return this.shouldTrack; }
        public Double getRequestedUsage() { return this.requestedUsage; }
        public void setShouldTrack(Boolean shouldTrack) { this.shouldTrack = shouldTrack; }
        public void setRequestedUsage(Double requestedUsage) { this.requestedUsage = requestedUsage; }
    }
    public static class FetchEntitlementsQueryInput {
        private String customerId;
        private String resourceId;
        private String environmentId;

        public FetchEntitlementsQueryInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class SubscriptionMigrationTaskFilterInput {
        private Iterable<SubscriptionMigrationTaskFilterInput> and;
        private Iterable<SubscriptionMigrationTaskFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput environmentId;
        private DateFieldComparisonInput createdAt;
        private TaskTypeFilterComparisonInput taskType;
        private TaskStatusFilterComparisonInput status;

        public SubscriptionMigrationTaskFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<SubscriptionMigrationTaskFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<SubscriptionMigrationTaskFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.taskType = new TaskTypeFilterComparisonInput((Map<String, Object>) args.get("taskType"));
                this.status = new TaskStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
            }
        }

        public Iterable<SubscriptionMigrationTaskFilterInput> getAnd() { return this.and; }
        public Iterable<SubscriptionMigrationTaskFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public TaskTypeFilterComparisonInput getTaskType() { return this.taskType; }
        public TaskStatusFilterComparisonInput getStatus() { return this.status; }
        public void setAnd(Iterable<SubscriptionMigrationTaskFilterInput> and) { this.and = and; }
        public void setOr(Iterable<SubscriptionMigrationTaskFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setTaskType(TaskTypeFilterComparisonInput taskType) { this.taskType = taskType; }
        public void setStatus(TaskStatusFilterComparisonInput status) { this.status = status; }
    }
    public static class TaskTypeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private TaskType eq;
        private TaskType neq;
        private TaskType gt;
        private TaskType gte;
        private TaskType lt;
        private TaskType lte;
        private TaskType like;
        private TaskType notLike;
        private TaskType iLike;
        private TaskType notILike;
        private Iterable<TaskType> in;
        private Iterable<TaskType> notIn;

        public TaskTypeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof TaskType) {
                    this.eq = (TaskType) args.get("eq");
                } else {
                    this.eq = TaskType.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof TaskType) {
                    this.neq = (TaskType) args.get("neq");
                } else {
                    this.neq = TaskType.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof TaskType) {
                    this.gt = (TaskType) args.get("gt");
                } else {
                    this.gt = TaskType.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof TaskType) {
                    this.gte = (TaskType) args.get("gte");
                } else {
                    this.gte = TaskType.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof TaskType) {
                    this.lt = (TaskType) args.get("lt");
                } else {
                    this.lt = TaskType.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof TaskType) {
                    this.lte = (TaskType) args.get("lte");
                } else {
                    this.lte = TaskType.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof TaskType) {
                    this.like = (TaskType) args.get("like");
                } else {
                    this.like = TaskType.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof TaskType) {
                    this.notLike = (TaskType) args.get("notLike");
                } else {
                    this.notLike = TaskType.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof TaskType) {
                    this.iLike = (TaskType) args.get("iLike");
                } else {
                    this.iLike = TaskType.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof TaskType) {
                    this.notILike = (TaskType) args.get("notILike");
                } else {
                    this.notILike = TaskType.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<TaskType>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<TaskType>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public TaskType getEq() { return this.eq; }
        public TaskType getNeq() { return this.neq; }
        public TaskType getGt() { return this.gt; }
        public TaskType getGte() { return this.gte; }
        public TaskType getLt() { return this.lt; }
        public TaskType getLte() { return this.lte; }
        public TaskType getLike() { return this.like; }
        public TaskType getNotLike() { return this.notLike; }
        public TaskType getILike() { return this.iLike; }
        public TaskType getNotILike() { return this.notILike; }
        public Iterable<TaskType> getIn() { return this.in; }
        public Iterable<TaskType> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(TaskType eq) { this.eq = eq; }
        public void setNeq(TaskType neq) { this.neq = neq; }
        public void setGt(TaskType gt) { this.gt = gt; }
        public void setGte(TaskType gte) { this.gte = gte; }
        public void setLt(TaskType lt) { this.lt = lt; }
        public void setLte(TaskType lte) { this.lte = lte; }
        public void setLike(TaskType like) { this.like = like; }
        public void setNotLike(TaskType notLike) { this.notLike = notLike; }
        public void setILike(TaskType iLike) { this.iLike = iLike; }
        public void setNotILike(TaskType notILike) { this.notILike = notILike; }
        public void setIn(Iterable<TaskType> in) { this.in = in; }
        public void setNotIn(Iterable<TaskType> notIn) { this.notIn = notIn; }
    }
    public static class TaskStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private TaskStatus eq;
        private TaskStatus neq;
        private TaskStatus gt;
        private TaskStatus gte;
        private TaskStatus lt;
        private TaskStatus lte;
        private TaskStatus like;
        private TaskStatus notLike;
        private TaskStatus iLike;
        private TaskStatus notILike;
        private Iterable<TaskStatus> in;
        private Iterable<TaskStatus> notIn;

        public TaskStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof TaskStatus) {
                    this.eq = (TaskStatus) args.get("eq");
                } else {
                    this.eq = TaskStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof TaskStatus) {
                    this.neq = (TaskStatus) args.get("neq");
                } else {
                    this.neq = TaskStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof TaskStatus) {
                    this.gt = (TaskStatus) args.get("gt");
                } else {
                    this.gt = TaskStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof TaskStatus) {
                    this.gte = (TaskStatus) args.get("gte");
                } else {
                    this.gte = TaskStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof TaskStatus) {
                    this.lt = (TaskStatus) args.get("lt");
                } else {
                    this.lt = TaskStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof TaskStatus) {
                    this.lte = (TaskStatus) args.get("lte");
                } else {
                    this.lte = TaskStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof TaskStatus) {
                    this.like = (TaskStatus) args.get("like");
                } else {
                    this.like = TaskStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof TaskStatus) {
                    this.notLike = (TaskStatus) args.get("notLike");
                } else {
                    this.notLike = TaskStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof TaskStatus) {
                    this.iLike = (TaskStatus) args.get("iLike");
                } else {
                    this.iLike = TaskStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof TaskStatus) {
                    this.notILike = (TaskStatus) args.get("notILike");
                } else {
                    this.notILike = TaskStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<TaskStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<TaskStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public TaskStatus getEq() { return this.eq; }
        public TaskStatus getNeq() { return this.neq; }
        public TaskStatus getGt() { return this.gt; }
        public TaskStatus getGte() { return this.gte; }
        public TaskStatus getLt() { return this.lt; }
        public TaskStatus getLte() { return this.lte; }
        public TaskStatus getLike() { return this.like; }
        public TaskStatus getNotLike() { return this.notLike; }
        public TaskStatus getILike() { return this.iLike; }
        public TaskStatus getNotILike() { return this.notILike; }
        public Iterable<TaskStatus> getIn() { return this.in; }
        public Iterable<TaskStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(TaskStatus eq) { this.eq = eq; }
        public void setNeq(TaskStatus neq) { this.neq = neq; }
        public void setGt(TaskStatus gt) { this.gt = gt; }
        public void setGte(TaskStatus gte) { this.gte = gte; }
        public void setLt(TaskStatus lt) { this.lt = lt; }
        public void setLte(TaskStatus lte) { this.lte = lte; }
        public void setLike(TaskStatus like) { this.like = like; }
        public void setNotLike(TaskStatus notLike) { this.notLike = notLike; }
        public void setILike(TaskStatus iLike) { this.iLike = iLike; }
        public void setNotILike(TaskStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<TaskStatus> in) { this.in = in; }
        public void setNotIn(Iterable<TaskStatus> notIn) { this.notIn = notIn; }
    }
    public static class SubscriptionMigrationTaskSortInput {
        private SubscriptionMigrationTaskSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public SubscriptionMigrationTaskSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof SubscriptionMigrationTaskSortFields) {
                    this.field = (SubscriptionMigrationTaskSortFields) args.get("field");
                } else {
                    this.field = SubscriptionMigrationTaskSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public SubscriptionMigrationTaskSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(SubscriptionMigrationTaskSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum SubscriptionMigrationTaskSortFields {
        id,
        environmentId,
        createdAt,
        taskType,
        status

    }

    public static class ImportIntegrationTaskFilterInput {
        private Iterable<ImportIntegrationTaskFilterInput> and;
        private Iterable<ImportIntegrationTaskFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput environmentId;
        private DateFieldComparisonInput createdAt;
        private TaskTypeFilterComparisonInput taskType;
        private TaskStatusFilterComparisonInput status;

        public ImportIntegrationTaskFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<ImportIntegrationTaskFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<ImportIntegrationTaskFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.taskType = new TaskTypeFilterComparisonInput((Map<String, Object>) args.get("taskType"));
                this.status = new TaskStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
            }
        }

        public Iterable<ImportIntegrationTaskFilterInput> getAnd() { return this.and; }
        public Iterable<ImportIntegrationTaskFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public TaskTypeFilterComparisonInput getTaskType() { return this.taskType; }
        public TaskStatusFilterComparisonInput getStatus() { return this.status; }
        public void setAnd(Iterable<ImportIntegrationTaskFilterInput> and) { this.and = and; }
        public void setOr(Iterable<ImportIntegrationTaskFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setTaskType(TaskTypeFilterComparisonInput taskType) { this.taskType = taskType; }
        public void setStatus(TaskStatusFilterComparisonInput status) { this.status = status; }
    }
    public static class ImportIntegrationTaskSortInput {
        private ImportIntegrationTaskSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public ImportIntegrationTaskSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof ImportIntegrationTaskSortFields) {
                    this.field = (ImportIntegrationTaskSortFields) args.get("field");
                } else {
                    this.field = ImportIntegrationTaskSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public ImportIntegrationTaskSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(ImportIntegrationTaskSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum ImportIntegrationTaskSortFields {
        id,
        environmentId,
        createdAt,
        taskType,
        status

    }

    public static class StripeProductSearchInput {
        private String environmentId;
        private String nextPage;
        private String productName;

        public StripeProductSearchInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.nextPage = (String) args.get("nextPage");
                this.productName = (String) args.get("productName");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getNextPage() { return this.nextPage; }
        public String getProductName() { return this.productName; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setNextPage(String nextPage) { this.nextPage = nextPage; }
        public void setProductName(String productName) { this.productName = productName; }
    }
    public static class StripeCustomerSearchInput {
        private String environmentId;
        private String nextPage;
        private String customerName;

        public StripeCustomerSearchInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.nextPage = (String) args.get("nextPage");
                this.customerName = (String) args.get("customerName");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getNextPage() { return this.nextPage; }
        public String getCustomerName() { return this.customerName; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setNextPage(String nextPage) { this.nextPage = nextPage; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
    }
    public static class StripeSubscriptionSearchInput {
        private String environmentId;
        private String nextPage;

        public StripeSubscriptionSearchInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.nextPage = (String) args.get("nextPage");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getNextPage() { return this.nextPage; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setNextPage(String nextPage) { this.nextPage = nextPage; }
    }
    public static class ProductFilterInput {
        private Iterable<ProductFilterInput> and;
        private Iterable<ProductFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput description;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;
        private BooleanFieldComparisonInput isDefaultProduct;

        public ProductFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<ProductFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<ProductFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.isDefaultProduct = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isDefaultProduct"));
            }
        }

        public Iterable<ProductFilterInput> getAnd() { return this.and; }
        public Iterable<ProductFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public BooleanFieldComparisonInput getIsDefaultProduct() { return this.isDefaultProduct; }
        public void setAnd(Iterable<ProductFilterInput> and) { this.and = and; }
        public void setOr(Iterable<ProductFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setIsDefaultProduct(BooleanFieldComparisonInput isDefaultProduct) { this.isDefaultProduct = isDefaultProduct; }
    }
    public static class ProductSortInput {
        private ProductSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public ProductSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof ProductSortFields) {
                    this.field = (ProductSortFields) args.get("field");
                } else {
                    this.field = ProductSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public ProductSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(ProductSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum ProductSortFields {
        id,
        displayName,
        refId,
        description,
        createdAt,
        updatedAt,
        environmentId,
        isDefaultProduct

    }

    public static class EnvironmentFilterInput {
        private Iterable<EnvironmentFilterInput> and;
        private Iterable<EnvironmentFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput slug;

        public EnvironmentFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<EnvironmentFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<EnvironmentFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.slug = new StringFieldComparisonInput((Map<String, Object>) args.get("slug"));
            }
        }

        public Iterable<EnvironmentFilterInput> getAnd() { return this.and; }
        public Iterable<EnvironmentFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getSlug() { return this.slug; }
        public void setAnd(Iterable<EnvironmentFilterInput> and) { this.and = and; }
        public void setOr(Iterable<EnvironmentFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setSlug(StringFieldComparisonInput slug) { this.slug = slug; }
    }
    public static class EnvironmentSortInput {
        private EnvironmentSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public EnvironmentSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof EnvironmentSortFields) {
                    this.field = (EnvironmentSortFields) args.get("field");
                } else {
                    this.field = EnvironmentSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public EnvironmentSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(EnvironmentSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum EnvironmentSortFields {
        id,
        createdAt,
        displayName,
        slug

    }

    public static class ExperimentStatsQueryInput {
        private String experimentRefId;
        private String environmentId;

        public ExperimentStatsQueryInput(Map<String, Object> args) {
            if (args != null) {
                this.experimentRefId = (String) args.get("experimentRefId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getExperimentRefId() { return this.experimentRefId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setExperimentRefId(String experimentRefId) { this.experimentRefId = experimentRefId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class ExperimentFilterInput {
        private Iterable<ExperimentFilterInput> and;
        private Iterable<ExperimentFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput refId;
        private DateFieldComparisonInput createdAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private ExperimentStatusFilterComparisonInput status;
        private ExperimentFilterCustomerFilterInput customers;

        public ExperimentFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<ExperimentFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<ExperimentFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.status = new ExperimentStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.customers = new ExperimentFilterCustomerFilterInput((Map<String, Object>) args.get("customers"));
            }
        }

        public Iterable<ExperimentFilterInput> getAnd() { return this.and; }
        public Iterable<ExperimentFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public ExperimentStatusFilterComparisonInput getStatus() { return this.status; }
        public ExperimentFilterCustomerFilterInput getCustomers() { return this.customers; }
        public void setAnd(Iterable<ExperimentFilterInput> and) { this.and = and; }
        public void setOr(Iterable<ExperimentFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setStatus(ExperimentStatusFilterComparisonInput status) { this.status = status; }
        public void setCustomers(ExperimentFilterCustomerFilterInput customers) { this.customers = customers; }
    }
    public static class ExperimentStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private ExperimentStatus eq;
        private ExperimentStatus neq;
        private ExperimentStatus gt;
        private ExperimentStatus gte;
        private ExperimentStatus lt;
        private ExperimentStatus lte;
        private ExperimentStatus like;
        private ExperimentStatus notLike;
        private ExperimentStatus iLike;
        private ExperimentStatus notILike;
        private Iterable<ExperimentStatus> in;
        private Iterable<ExperimentStatus> notIn;

        public ExperimentStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof ExperimentStatus) {
                    this.eq = (ExperimentStatus) args.get("eq");
                } else {
                    this.eq = ExperimentStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof ExperimentStatus) {
                    this.neq = (ExperimentStatus) args.get("neq");
                } else {
                    this.neq = ExperimentStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof ExperimentStatus) {
                    this.gt = (ExperimentStatus) args.get("gt");
                } else {
                    this.gt = ExperimentStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof ExperimentStatus) {
                    this.gte = (ExperimentStatus) args.get("gte");
                } else {
                    this.gte = ExperimentStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof ExperimentStatus) {
                    this.lt = (ExperimentStatus) args.get("lt");
                } else {
                    this.lt = ExperimentStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof ExperimentStatus) {
                    this.lte = (ExperimentStatus) args.get("lte");
                } else {
                    this.lte = ExperimentStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof ExperimentStatus) {
                    this.like = (ExperimentStatus) args.get("like");
                } else {
                    this.like = ExperimentStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof ExperimentStatus) {
                    this.notLike = (ExperimentStatus) args.get("notLike");
                } else {
                    this.notLike = ExperimentStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof ExperimentStatus) {
                    this.iLike = (ExperimentStatus) args.get("iLike");
                } else {
                    this.iLike = ExperimentStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof ExperimentStatus) {
                    this.notILike = (ExperimentStatus) args.get("notILike");
                } else {
                    this.notILike = ExperimentStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<ExperimentStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<ExperimentStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public ExperimentStatus getEq() { return this.eq; }
        public ExperimentStatus getNeq() { return this.neq; }
        public ExperimentStatus getGt() { return this.gt; }
        public ExperimentStatus getGte() { return this.gte; }
        public ExperimentStatus getLt() { return this.lt; }
        public ExperimentStatus getLte() { return this.lte; }
        public ExperimentStatus getLike() { return this.like; }
        public ExperimentStatus getNotLike() { return this.notLike; }
        public ExperimentStatus getILike() { return this.iLike; }
        public ExperimentStatus getNotILike() { return this.notILike; }
        public Iterable<ExperimentStatus> getIn() { return this.in; }
        public Iterable<ExperimentStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(ExperimentStatus eq) { this.eq = eq; }
        public void setNeq(ExperimentStatus neq) { this.neq = neq; }
        public void setGt(ExperimentStatus gt) { this.gt = gt; }
        public void setGte(ExperimentStatus gte) { this.gte = gte; }
        public void setLt(ExperimentStatus lt) { this.lt = lt; }
        public void setLte(ExperimentStatus lte) { this.lte = lte; }
        public void setLike(ExperimentStatus like) { this.like = like; }
        public void setNotLike(ExperimentStatus notLike) { this.notLike = notLike; }
        public void setILike(ExperimentStatus iLike) { this.iLike = iLike; }
        public void setNotILike(ExperimentStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<ExperimentStatus> in) { this.in = in; }
        public void setNotIn(Iterable<ExperimentStatus> notIn) { this.notIn = notIn; }
    }
    public static class ExperimentFilterCustomerFilterInput {
        private Iterable<ExperimentFilterCustomerFilterInput> and;
        private Iterable<ExperimentFilterCustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;

        public ExperimentFilterCustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<ExperimentFilterCustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<ExperimentFilterCustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
            }
        }

        public Iterable<ExperimentFilterCustomerFilterInput> getAnd() { return this.and; }
        public Iterable<ExperimentFilterCustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public void setAnd(Iterable<ExperimentFilterCustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<ExperimentFilterCustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
    }
    public static class ExperimentSortInput {
        private ExperimentSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public ExperimentSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof ExperimentSortFields) {
                    this.field = (ExperimentSortFields) args.get("field");
                } else {
                    this.field = ExperimentSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public ExperimentSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(ExperimentSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum ExperimentSortFields {
        id,
        name,
        refId,
        createdAt,
        environmentId,
        productId,
        status

    }

    public static class CouponFilterInput {
        private Iterable<CouponFilterInput> and;
        private Iterable<CouponFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput description;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;
        private CouponTypeFilterComparisonInput type;
        private CouponStatusFilterComparisonInput status;
        private StringFieldComparisonInput billingId;
        private CouponFilterCustomerFilterInput customers;

        public CouponFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CouponFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CouponFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.type = new CouponTypeFilterComparisonInput((Map<String, Object>) args.get("type"));
                this.status = new CouponStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.customers = new CouponFilterCustomerFilterInput((Map<String, Object>) args.get("customers"));
            }
        }

        public Iterable<CouponFilterInput> getAnd() { return this.and; }
        public Iterable<CouponFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public CouponTypeFilterComparisonInput getType() { return this.type; }
        public CouponStatusFilterComparisonInput getStatus() { return this.status; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public CouponFilterCustomerFilterInput getCustomers() { return this.customers; }
        public void setAnd(Iterable<CouponFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CouponFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setType(CouponTypeFilterComparisonInput type) { this.type = type; }
        public void setStatus(CouponStatusFilterComparisonInput status) { this.status = status; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCustomers(CouponFilterCustomerFilterInput customers) { this.customers = customers; }
    }
    public static class CouponTypeFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private CouponType eq;
        private CouponType neq;
        private CouponType gt;
        private CouponType gte;
        private CouponType lt;
        private CouponType lte;
        private CouponType like;
        private CouponType notLike;
        private CouponType iLike;
        private CouponType notILike;
        private Iterable<CouponType> in;
        private Iterable<CouponType> notIn;

        public CouponTypeFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof CouponType) {
                    this.eq = (CouponType) args.get("eq");
                } else {
                    this.eq = CouponType.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof CouponType) {
                    this.neq = (CouponType) args.get("neq");
                } else {
                    this.neq = CouponType.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof CouponType) {
                    this.gt = (CouponType) args.get("gt");
                } else {
                    this.gt = CouponType.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof CouponType) {
                    this.gte = (CouponType) args.get("gte");
                } else {
                    this.gte = CouponType.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof CouponType) {
                    this.lt = (CouponType) args.get("lt");
                } else {
                    this.lt = CouponType.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof CouponType) {
                    this.lte = (CouponType) args.get("lte");
                } else {
                    this.lte = CouponType.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof CouponType) {
                    this.like = (CouponType) args.get("like");
                } else {
                    this.like = CouponType.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof CouponType) {
                    this.notLike = (CouponType) args.get("notLike");
                } else {
                    this.notLike = CouponType.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof CouponType) {
                    this.iLike = (CouponType) args.get("iLike");
                } else {
                    this.iLike = CouponType.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof CouponType) {
                    this.notILike = (CouponType) args.get("notILike");
                } else {
                    this.notILike = CouponType.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<CouponType>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<CouponType>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public CouponType getEq() { return this.eq; }
        public CouponType getNeq() { return this.neq; }
        public CouponType getGt() { return this.gt; }
        public CouponType getGte() { return this.gte; }
        public CouponType getLt() { return this.lt; }
        public CouponType getLte() { return this.lte; }
        public CouponType getLike() { return this.like; }
        public CouponType getNotLike() { return this.notLike; }
        public CouponType getILike() { return this.iLike; }
        public CouponType getNotILike() { return this.notILike; }
        public Iterable<CouponType> getIn() { return this.in; }
        public Iterable<CouponType> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(CouponType eq) { this.eq = eq; }
        public void setNeq(CouponType neq) { this.neq = neq; }
        public void setGt(CouponType gt) { this.gt = gt; }
        public void setGte(CouponType gte) { this.gte = gte; }
        public void setLt(CouponType lt) { this.lt = lt; }
        public void setLte(CouponType lte) { this.lte = lte; }
        public void setLike(CouponType like) { this.like = like; }
        public void setNotLike(CouponType notLike) { this.notLike = notLike; }
        public void setILike(CouponType iLike) { this.iLike = iLike; }
        public void setNotILike(CouponType notILike) { this.notILike = notILike; }
        public void setIn(Iterable<CouponType> in) { this.in = in; }
        public void setNotIn(Iterable<CouponType> notIn) { this.notIn = notIn; }
    }
    public static class CouponStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private CouponStatus eq;
        private CouponStatus neq;
        private CouponStatus gt;
        private CouponStatus gte;
        private CouponStatus lt;
        private CouponStatus lte;
        private CouponStatus like;
        private CouponStatus notLike;
        private CouponStatus iLike;
        private CouponStatus notILike;
        private Iterable<CouponStatus> in;
        private Iterable<CouponStatus> notIn;

        public CouponStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof CouponStatus) {
                    this.eq = (CouponStatus) args.get("eq");
                } else {
                    this.eq = CouponStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof CouponStatus) {
                    this.neq = (CouponStatus) args.get("neq");
                } else {
                    this.neq = CouponStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof CouponStatus) {
                    this.gt = (CouponStatus) args.get("gt");
                } else {
                    this.gt = CouponStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof CouponStatus) {
                    this.gte = (CouponStatus) args.get("gte");
                } else {
                    this.gte = CouponStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof CouponStatus) {
                    this.lt = (CouponStatus) args.get("lt");
                } else {
                    this.lt = CouponStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof CouponStatus) {
                    this.lte = (CouponStatus) args.get("lte");
                } else {
                    this.lte = CouponStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof CouponStatus) {
                    this.like = (CouponStatus) args.get("like");
                } else {
                    this.like = CouponStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof CouponStatus) {
                    this.notLike = (CouponStatus) args.get("notLike");
                } else {
                    this.notLike = CouponStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof CouponStatus) {
                    this.iLike = (CouponStatus) args.get("iLike");
                } else {
                    this.iLike = CouponStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof CouponStatus) {
                    this.notILike = (CouponStatus) args.get("notILike");
                } else {
                    this.notILike = CouponStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<CouponStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<CouponStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public CouponStatus getEq() { return this.eq; }
        public CouponStatus getNeq() { return this.neq; }
        public CouponStatus getGt() { return this.gt; }
        public CouponStatus getGte() { return this.gte; }
        public CouponStatus getLt() { return this.lt; }
        public CouponStatus getLte() { return this.lte; }
        public CouponStatus getLike() { return this.like; }
        public CouponStatus getNotLike() { return this.notLike; }
        public CouponStatus getILike() { return this.iLike; }
        public CouponStatus getNotILike() { return this.notILike; }
        public Iterable<CouponStatus> getIn() { return this.in; }
        public Iterable<CouponStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(CouponStatus eq) { this.eq = eq; }
        public void setNeq(CouponStatus neq) { this.neq = neq; }
        public void setGt(CouponStatus gt) { this.gt = gt; }
        public void setGte(CouponStatus gte) { this.gte = gte; }
        public void setLt(CouponStatus lt) { this.lt = lt; }
        public void setLte(CouponStatus lte) { this.lte = lte; }
        public void setLike(CouponStatus like) { this.like = like; }
        public void setNotLike(CouponStatus notLike) { this.notLike = notLike; }
        public void setILike(CouponStatus iLike) { this.iLike = iLike; }
        public void setNotILike(CouponStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<CouponStatus> in) { this.in = in; }
        public void setNotIn(Iterable<CouponStatus> notIn) { this.notIn = notIn; }
    }
    public static class CouponFilterCustomerFilterInput {
        private Iterable<CouponFilterCustomerFilterInput> and;
        private Iterable<CouponFilterCustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;

        public CouponFilterCustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CouponFilterCustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CouponFilterCustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
            }
        }

        public Iterable<CouponFilterCustomerFilterInput> getAnd() { return this.and; }
        public Iterable<CouponFilterCustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public void setAnd(Iterable<CouponFilterCustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CouponFilterCustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
    }
    public static class CouponSortInput {
        private CouponSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public CouponSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof CouponSortFields) {
                    this.field = (CouponSortFields) args.get("field");
                } else {
                    this.field = CouponSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public CouponSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(CouponSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum CouponSortFields {
        id,
        name,
        refId,
        description,
        createdAt,
        updatedAt,
        environmentId,
        type,
        status,
        billingId

    }

    public static class IntegrationFilterInput {
        private Iterable<IntegrationFilterInput> and;
        private Iterable<IntegrationFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private StringFieldComparisonInput environmentId;
        private VendorIdentifierFilterComparisonInput vendorIdentifier;

        public IntegrationFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<IntegrationFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<IntegrationFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.vendorIdentifier = new VendorIdentifierFilterComparisonInput((Map<String, Object>) args.get("vendorIdentifier"));
            }
        }

        public Iterable<IntegrationFilterInput> getAnd() { return this.and; }
        public Iterable<IntegrationFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public VendorIdentifierFilterComparisonInput getVendorIdentifier() { return this.vendorIdentifier; }
        public void setAnd(Iterable<IntegrationFilterInput> and) { this.and = and; }
        public void setOr(Iterable<IntegrationFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifierFilterComparisonInput vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
    }
    public static class VendorIdentifierFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private VendorIdentifier eq;
        private VendorIdentifier neq;
        private VendorIdentifier gt;
        private VendorIdentifier gte;
        private VendorIdentifier lt;
        private VendorIdentifier lte;
        private VendorIdentifier like;
        private VendorIdentifier notLike;
        private VendorIdentifier iLike;
        private VendorIdentifier notILike;
        private Iterable<VendorIdentifier> in;
        private Iterable<VendorIdentifier> notIn;

        public VendorIdentifierFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof VendorIdentifier) {
                    this.eq = (VendorIdentifier) args.get("eq");
                } else {
                    this.eq = VendorIdentifier.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof VendorIdentifier) {
                    this.neq = (VendorIdentifier) args.get("neq");
                } else {
                    this.neq = VendorIdentifier.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof VendorIdentifier) {
                    this.gt = (VendorIdentifier) args.get("gt");
                } else {
                    this.gt = VendorIdentifier.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof VendorIdentifier) {
                    this.gte = (VendorIdentifier) args.get("gte");
                } else {
                    this.gte = VendorIdentifier.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof VendorIdentifier) {
                    this.lt = (VendorIdentifier) args.get("lt");
                } else {
                    this.lt = VendorIdentifier.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof VendorIdentifier) {
                    this.lte = (VendorIdentifier) args.get("lte");
                } else {
                    this.lte = VendorIdentifier.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof VendorIdentifier) {
                    this.like = (VendorIdentifier) args.get("like");
                } else {
                    this.like = VendorIdentifier.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof VendorIdentifier) {
                    this.notLike = (VendorIdentifier) args.get("notLike");
                } else {
                    this.notLike = VendorIdentifier.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof VendorIdentifier) {
                    this.iLike = (VendorIdentifier) args.get("iLike");
                } else {
                    this.iLike = VendorIdentifier.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof VendorIdentifier) {
                    this.notILike = (VendorIdentifier) args.get("notILike");
                } else {
                    this.notILike = VendorIdentifier.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<VendorIdentifier>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<VendorIdentifier>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public VendorIdentifier getEq() { return this.eq; }
        public VendorIdentifier getNeq() { return this.neq; }
        public VendorIdentifier getGt() { return this.gt; }
        public VendorIdentifier getGte() { return this.gte; }
        public VendorIdentifier getLt() { return this.lt; }
        public VendorIdentifier getLte() { return this.lte; }
        public VendorIdentifier getLike() { return this.like; }
        public VendorIdentifier getNotLike() { return this.notLike; }
        public VendorIdentifier getILike() { return this.iLike; }
        public VendorIdentifier getNotILike() { return this.notILike; }
        public Iterable<VendorIdentifier> getIn() { return this.in; }
        public Iterable<VendorIdentifier> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(VendorIdentifier eq) { this.eq = eq; }
        public void setNeq(VendorIdentifier neq) { this.neq = neq; }
        public void setGt(VendorIdentifier gt) { this.gt = gt; }
        public void setGte(VendorIdentifier gte) { this.gte = gte; }
        public void setLt(VendorIdentifier lt) { this.lt = lt; }
        public void setLte(VendorIdentifier lte) { this.lte = lte; }
        public void setLike(VendorIdentifier like) { this.like = like; }
        public void setNotLike(VendorIdentifier notLike) { this.notLike = notLike; }
        public void setILike(VendorIdentifier iLike) { this.iLike = iLike; }
        public void setNotILike(VendorIdentifier notILike) { this.notILike = notILike; }
        public void setIn(Iterable<VendorIdentifier> in) { this.in = in; }
        public void setNotIn(Iterable<VendorIdentifier> notIn) { this.notIn = notIn; }
    }
    public static class IntegrationSortInput {
        private IntegrationSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public IntegrationSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof IntegrationSortFields) {
                    this.field = (IntegrationSortFields) args.get("field");
                } else {
                    this.field = IntegrationSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public IntegrationSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(IntegrationSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum IntegrationSortFields {
        id,
        createdAt,
        environmentId,
        vendorIdentifier

    }

    public static class GetActiveSubscriptionsInput {
        private String customerId;
        private String environmentId;
        private String resourceId;
        private Iterable<String> resourceIds;

        public GetActiveSubscriptionsInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
                this.resourceId = (String) args.get("resourceId");
                this.resourceIds = (Iterable<String>) args.get("resourceIds");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getResourceId() { return this.resourceId; }
        public Iterable<String> getResourceIds() { return this.resourceIds; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setResourceIds(Iterable<String> resourceIds) { this.resourceIds = resourceIds; }
    }
    public static class FeatureFilterInput {
        private Iterable<FeatureFilterInput> and;
        private Iterable<FeatureFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput description;
        private FeatureTypeFilterComparisonInput featureType;
        private MeterTypeFilterComparisonInput meterType;
        private FeatureStatusFilterComparisonInput featureStatus;
        private StringFieldComparisonInput environmentId;

        public FeatureFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<FeatureFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<FeatureFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.featureType = new FeatureTypeFilterComparisonInput((Map<String, Object>) args.get("featureType"));
                this.meterType = new MeterTypeFilterComparisonInput((Map<String, Object>) args.get("meterType"));
                this.featureStatus = new FeatureStatusFilterComparisonInput((Map<String, Object>) args.get("featureStatus"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<FeatureFilterInput> getAnd() { return this.and; }
        public Iterable<FeatureFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public FeatureTypeFilterComparisonInput getFeatureType() { return this.featureType; }
        public MeterTypeFilterComparisonInput getMeterType() { return this.meterType; }
        public FeatureStatusFilterComparisonInput getFeatureStatus() { return this.featureStatus; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<FeatureFilterInput> and) { this.and = and; }
        public void setOr(Iterable<FeatureFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setFeatureType(FeatureTypeFilterComparisonInput featureType) { this.featureType = featureType; }
        public void setMeterType(MeterTypeFilterComparisonInput meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatusFilterComparisonInput featureStatus) { this.featureStatus = featureStatus; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class FeatureSortInput {
        private FeatureSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public FeatureSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof FeatureSortFields) {
                    this.field = (FeatureSortFields) args.get("field");
                } else {
                    this.field = FeatureSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public FeatureSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(FeatureSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum FeatureSortFields {
        id,
        displayName,
        refId,
        createdAt,
        updatedAt,
        description,
        featureType,
        meterType,
        featureStatus,
        environmentId

    }

    public static class PackageEntitlementFilterInput {
        private Iterable<PackageEntitlementFilterInput> and;
        private Iterable<PackageEntitlementFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput packageId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;
        private PackageEntitlementFilterPackageDtoFilterInput packageName;
        private PackageEntitlementFilterFeatureFilterInput feature;

        public PackageEntitlementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PackageEntitlementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PackageEntitlementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.packageId = new StringFieldComparisonInput((Map<String, Object>) args.get("packageId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.packageName = new PackageEntitlementFilterPackageDtoFilterInput((Map<String, Object>) args.get("packageName"));
                this.feature = new PackageEntitlementFilterFeatureFilterInput((Map<String, Object>) args.get("feature"));
            }
        }

        public Iterable<PackageEntitlementFilterInput> getAnd() { return this.and; }
        public Iterable<PackageEntitlementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getPackageId() { return this.packageId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public PackageEntitlementFilterPackageDtoFilterInput getPackage() { return this.packageName; }
        public PackageEntitlementFilterFeatureFilterInput getFeature() { return this.feature; }
        public void setAnd(Iterable<PackageEntitlementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PackageEntitlementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setPackageId(StringFieldComparisonInput packageId) { this.packageId = packageId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setPackage(PackageEntitlementFilterPackageDtoFilterInput packageName) { this.packageName = packageName; }
        public void setFeature(PackageEntitlementFilterFeatureFilterInput feature) { this.feature = feature; }
    }
    public static class PackageEntitlementFilterPackageDtoFilterInput {
        private Iterable<PackageEntitlementFilterPackageDtoFilterInput> and;
        private Iterable<PackageEntitlementFilterPackageDtoFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public PackageEntitlementFilterPackageDtoFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PackageEntitlementFilterPackageDtoFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PackageEntitlementFilterPackageDtoFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<PackageEntitlementFilterPackageDtoFilterInput> getAnd() { return this.and; }
        public Iterable<PackageEntitlementFilterPackageDtoFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<PackageEntitlementFilterPackageDtoFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PackageEntitlementFilterPackageDtoFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class PackageEntitlementFilterFeatureFilterInput {
        private Iterable<PackageEntitlementFilterFeatureFilterInput> and;
        private Iterable<PackageEntitlementFilterFeatureFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput description;
        private FeatureTypeFilterComparisonInput featureType;
        private MeterTypeFilterComparisonInput meterType;
        private FeatureStatusFilterComparisonInput featureStatus;
        private StringFieldComparisonInput environmentId;

        public PackageEntitlementFilterFeatureFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PackageEntitlementFilterFeatureFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PackageEntitlementFilterFeatureFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.featureType = new FeatureTypeFilterComparisonInput((Map<String, Object>) args.get("featureType"));
                this.meterType = new MeterTypeFilterComparisonInput((Map<String, Object>) args.get("meterType"));
                this.featureStatus = new FeatureStatusFilterComparisonInput((Map<String, Object>) args.get("featureStatus"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<PackageEntitlementFilterFeatureFilterInput> getAnd() { return this.and; }
        public Iterable<PackageEntitlementFilterFeatureFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public FeatureTypeFilterComparisonInput getFeatureType() { return this.featureType; }
        public MeterTypeFilterComparisonInput getMeterType() { return this.meterType; }
        public FeatureStatusFilterComparisonInput getFeatureStatus() { return this.featureStatus; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<PackageEntitlementFilterFeatureFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PackageEntitlementFilterFeatureFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setFeatureType(FeatureTypeFilterComparisonInput featureType) { this.featureType = featureType; }
        public void setMeterType(MeterTypeFilterComparisonInput meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatusFilterComparisonInput featureStatus) { this.featureStatus = featureStatus; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class PackageEntitlementSortInput {
        private PackageEntitlementSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public PackageEntitlementSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof PackageEntitlementSortFields) {
                    this.field = (PackageEntitlementSortFields) args.get("field");
                } else {
                    this.field = PackageEntitlementSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public PackageEntitlementSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(PackageEntitlementSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum PackageEntitlementSortFields {
        id,
        packageId,
        createdAt,
        updatedAt,
        environmentId

    }

    public static class CustomerPortalInput {
        private String productId;
        private String customerId;
        private String resourceId;

        public CustomerPortalInput(Map<String, Object> args) {
            if (args != null) {
                this.productId = (String) args.get("productId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
            }
        }

        public String getProductId() { return this.productId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    }
    public static class GetCustomerByRefIdInput {
        private String customerId;
        private String environmentId;

        public GetCustomerByRefIdInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class GetWidgetConfigurationInput {
        private String environmentId;

        public GetWidgetConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class CustomerResourceFilterInput {
        private Iterable<CustomerResourceFilterInput> and;
        private Iterable<CustomerResourceFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput resourceId;
        private DateFieldComparisonInput createdAt;
        private CustomerResourceFilterCustomerFilterInput customer;
        private CustomerResourceFilterCustomerSubscriptionFilterInput subscriptions;

        public CustomerResourceFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerResourceFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerResourceFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.customer = new CustomerResourceFilterCustomerFilterInput((Map<String, Object>) args.get("customer"));
                this.subscriptions = new CustomerResourceFilterCustomerSubscriptionFilterInput((Map<String, Object>) args.get("subscriptions"));
            }
        }

        public Iterable<CustomerResourceFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerResourceFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public CustomerResourceFilterCustomerFilterInput getCustomer() { return this.customer; }
        public CustomerResourceFilterCustomerSubscriptionFilterInput getSubscriptions() { return this.subscriptions; }
        public void setAnd(Iterable<CustomerResourceFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerResourceFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setCustomer(CustomerResourceFilterCustomerFilterInput customer) { this.customer = customer; }
        public void setSubscriptions(CustomerResourceFilterCustomerSubscriptionFilterInput subscriptions) { this.subscriptions = subscriptions; }
    }
    public static class CustomerResourceFilterCustomerFilterInput {
        private Iterable<CustomerResourceFilterCustomerFilterInput> and;
        private Iterable<CustomerResourceFilterCustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;

        public CustomerResourceFilterCustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerResourceFilterCustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerResourceFilterCustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
            }
        }

        public Iterable<CustomerResourceFilterCustomerFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerResourceFilterCustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public void setAnd(Iterable<CustomerResourceFilterCustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerResourceFilterCustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
    }
    public static class CustomerResourceFilterCustomerSubscriptionFilterInput {
        private Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> and;
        private Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> or;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput startDate;
        private DateFieldComparisonInput endDate;
        private DateFieldComparisonInput cancellationDate;
        private DateFieldComparisonInput trialEndDate;
        private DateFieldComparisonInput effectiveEndDate;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput oldBillingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmLinkUrl;
        private SubscriptionStatusFilterComparisonInput status;
        private SubscriptionCancelReasonFilterComparisonInput cancelReason;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput subscriptionId;
        private StringFieldComparisonInput resourceId;
        private PricingTypeFilterComparisonInput pricingType;
        private PaymentCollectionFilterComparisonInput paymentCollection;

        public CustomerResourceFilterCustomerSubscriptionFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput>) args.get("or");
                }
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.startDate = new DateFieldComparisonInput((Map<String, Object>) args.get("startDate"));
                this.endDate = new DateFieldComparisonInput((Map<String, Object>) args.get("endDate"));
                this.cancellationDate = new DateFieldComparisonInput((Map<String, Object>) args.get("cancellationDate"));
                this.trialEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("trialEndDate"));
                this.effectiveEndDate = new DateFieldComparisonInput((Map<String, Object>) args.get("effectiveEndDate"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.oldBillingId = new StringFieldComparisonInput((Map<String, Object>) args.get("oldBillingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmLinkUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmLinkUrl"));
                this.status = new SubscriptionStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.cancelReason = new SubscriptionCancelReasonFilterComparisonInput((Map<String, Object>) args.get("cancelReason"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.subscriptionId = new StringFieldComparisonInput((Map<String, Object>) args.get("subscriptionId"));
                this.resourceId = new StringFieldComparisonInput((Map<String, Object>) args.get("resourceId"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.paymentCollection = new PaymentCollectionFilterComparisonInput((Map<String, Object>) args.get("paymentCollection"));
            }
        }

        public Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> getAnd() { return this.and; }
        public Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getStartDate() { return this.startDate; }
        public DateFieldComparisonInput getEndDate() { return this.endDate; }
        public DateFieldComparisonInput getCancellationDate() { return this.cancellationDate; }
        public DateFieldComparisonInput getTrialEndDate() { return this.trialEndDate; }
        public DateFieldComparisonInput getEffectiveEndDate() { return this.effectiveEndDate; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getOldBillingId() { return this.oldBillingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmLinkUrl() { return this.crmLinkUrl; }
        public SubscriptionStatusFilterComparisonInput getStatus() { return this.status; }
        public SubscriptionCancelReasonFilterComparisonInput getCancelReason() { return this.cancelReason; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getSubscriptionId() { return this.subscriptionId; }
        public StringFieldComparisonInput getResourceId() { return this.resourceId; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public PaymentCollectionFilterComparisonInput getPaymentCollection() { return this.paymentCollection; }
        public void setAnd(Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> and) { this.and = and; }
        public void setOr(Iterable<CustomerResourceFilterCustomerSubscriptionFilterInput> or) { this.or = or; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setStartDate(DateFieldComparisonInput startDate) { this.startDate = startDate; }
        public void setEndDate(DateFieldComparisonInput endDate) { this.endDate = endDate; }
        public void setCancellationDate(DateFieldComparisonInput cancellationDate) { this.cancellationDate = cancellationDate; }
        public void setTrialEndDate(DateFieldComparisonInput trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setEffectiveEndDate(DateFieldComparisonInput effectiveEndDate) { this.effectiveEndDate = effectiveEndDate; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setOldBillingId(StringFieldComparisonInput oldBillingId) { this.oldBillingId = oldBillingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmLinkUrl(StringFieldComparisonInput crmLinkUrl) { this.crmLinkUrl = crmLinkUrl; }
        public void setStatus(SubscriptionStatusFilterComparisonInput status) { this.status = status; }
        public void setCancelReason(SubscriptionCancelReasonFilterComparisonInput cancelReason) { this.cancelReason = cancelReason; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setSubscriptionId(StringFieldComparisonInput subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setResourceId(StringFieldComparisonInput resourceId) { this.resourceId = resourceId; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setPaymentCollection(PaymentCollectionFilterComparisonInput paymentCollection) { this.paymentCollection = paymentCollection; }
    }
    public static class CustomerResourceSortInput {
        private CustomerResourceSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public CustomerResourceSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof CustomerResourceSortFields) {
                    this.field = (CustomerResourceSortFields) args.get("field");
                } else {
                    this.field = CustomerResourceSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public CustomerResourceSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(CustomerResourceSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum CustomerResourceSortFields {
        environmentId,
        resourceId,
        createdAt

    }

    public static class GetPaywallInput {
        private String environmentId;
        private String productId;
        private String customerId;
        private String resourceId;
        private String billingCountryCode;
        private Boolean fetchAllCountriesPrices;
        private WidgetType context;

        public GetPaywallInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.productId = (String) args.get("productId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.billingCountryCode = (String) args.get("billingCountryCode");
                this.fetchAllCountriesPrices = (Boolean) args.get("fetchAllCountriesPrices");
                if (args.get("context") instanceof WidgetType) {
                    this.context = (WidgetType) args.get("context");
                } else {
                    this.context = WidgetType.valueOf((String) args.get("context"));
                }
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getProductId() { return this.productId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Boolean getFetchAllCountriesPrices() { return this.fetchAllCountriesPrices; }
        public WidgetType getContext() { return this.context; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setFetchAllCountriesPrices(Boolean fetchAllCountriesPrices) { this.fetchAllCountriesPrices = fetchAllCountriesPrices; }
        public void setContext(WidgetType context) { this.context = context; }
    }
    public static class GetPackageByRefIdInput {
        private String refId;
        private Double versionNumber;
        private String environmentId;

        public GetPackageByRefIdInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.versionNumber = (Double) args.get("versionNumber");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getRefId() { return this.refId; }
        public Double getVersionNumber() { return this.versionNumber; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setVersionNumber(Double versionNumber) { this.versionNumber = versionNumber; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class PlanFilterInput {
        private Iterable<PlanFilterInput> and;
        private Iterable<PlanFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;
        private PlanFilterProductFilterInput product;
        private PlanFilterAddonFilterInput compatibleAddons;

        public PlanFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PlanFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PlanFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
                this.product = new PlanFilterProductFilterInput((Map<String, Object>) args.get("product"));
                this.compatibleAddons = new PlanFilterAddonFilterInput((Map<String, Object>) args.get("compatibleAddons"));
            }
        }

        public Iterable<PlanFilterInput> getAnd() { return this.and; }
        public Iterable<PlanFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public PlanFilterProductFilterInput getProduct() { return this.product; }
        public PlanFilterAddonFilterInput getCompatibleAddons() { return this.compatibleAddons; }
        public void setAnd(Iterable<PlanFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PlanFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
        public void setProduct(PlanFilterProductFilterInput product) { this.product = product; }
        public void setCompatibleAddons(PlanFilterAddonFilterInput compatibleAddons) { this.compatibleAddons = compatibleAddons; }
    }
    public static class PlanFilterProductFilterInput {
        private Iterable<PlanFilterProductFilterInput> and;
        private Iterable<PlanFilterProductFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput description;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput environmentId;
        private BooleanFieldComparisonInput isDefaultProduct;

        public PlanFilterProductFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PlanFilterProductFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PlanFilterProductFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.isDefaultProduct = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isDefaultProduct"));
            }
        }

        public Iterable<PlanFilterProductFilterInput> getAnd() { return this.and; }
        public Iterable<PlanFilterProductFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public BooleanFieldComparisonInput getIsDefaultProduct() { return this.isDefaultProduct; }
        public void setAnd(Iterable<PlanFilterProductFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PlanFilterProductFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setIsDefaultProduct(BooleanFieldComparisonInput isDefaultProduct) { this.isDefaultProduct = isDefaultProduct; }
    }
    public static class PlanFilterAddonFilterInput {
        private Iterable<PlanFilterAddonFilterInput> and;
        private Iterable<PlanFilterAddonFilterInput> or;
        private StringFieldComparisonInput id;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput displayName;
        private PackageStatusFilterComparisonInput status;
        private PricingTypeFilterComparisonInput pricingType;
        private StringFieldComparisonInput description;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput productId;
        private BooleanFieldComparisonInput isLatest;
        private IntFieldComparisonInput versionNumber;

        public PlanFilterAddonFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<PlanFilterAddonFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<PlanFilterAddonFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.status = new PackageStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.pricingType = new PricingTypeFilterComparisonInput((Map<String, Object>) args.get("pricingType"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.productId = new StringFieldComparisonInput((Map<String, Object>) args.get("productId"));
                this.isLatest = new BooleanFieldComparisonInput((Map<String, Object>) args.get("isLatest"));
                this.versionNumber = new IntFieldComparisonInput((Map<String, Object>) args.get("versionNumber"));
            }
        }

        public Iterable<PlanFilterAddonFilterInput> getAnd() { return this.and; }
        public Iterable<PlanFilterAddonFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public PackageStatusFilterComparisonInput getStatus() { return this.status; }
        public PricingTypeFilterComparisonInput getPricingType() { return this.pricingType; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getProductId() { return this.productId; }
        public BooleanFieldComparisonInput getIsLatest() { return this.isLatest; }
        public IntFieldComparisonInput getVersionNumber() { return this.versionNumber; }
        public void setAnd(Iterable<PlanFilterAddonFilterInput> and) { this.and = and; }
        public void setOr(Iterable<PlanFilterAddonFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setStatus(PackageStatusFilterComparisonInput status) { this.status = status; }
        public void setPricingType(PricingTypeFilterComparisonInput pricingType) { this.pricingType = pricingType; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setProductId(StringFieldComparisonInput productId) { this.productId = productId; }
        public void setIsLatest(BooleanFieldComparisonInput isLatest) { this.isLatest = isLatest; }
        public void setVersionNumber(IntFieldComparisonInput versionNumber) { this.versionNumber = versionNumber; }
    }
    public static class PlanSortInput {
        private PlanSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public PlanSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof PlanSortFields) {
                    this.field = (PlanSortFields) args.get("field");
                } else {
                    this.field = PlanSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public PlanSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(PlanSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum PlanSortFields {
        id,
        createdAt,
        updatedAt,
        refId,
        billingId,
        displayName,
        status,
        pricingType,
        description,
        environmentId,
        productId,
        isLatest,
        versionNumber

    }

    public static class UsageHistoryInput {
        private Object startDate;
        private Object endDate;
        private String featureRefId;
        private String customerRefId;
        private String resourceRefId;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private String environmentId;

        public UsageHistoryInput(Map<String, Object> args) {
            if (args != null) {
                this.startDate = (Object) args.get("startDate");
                this.endDate = (Object) args.get("endDate");
                this.featureRefId = (String) args.get("featureRefId");
                this.customerRefId = (String) args.get("customerRefId");
                this.resourceRefId = (String) args.get("resourceRefId");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Object getStartDate() { return this.startDate; }
        public Object getEndDate() { return this.endDate; }
        public String getFeatureRefId() { return this.featureRefId; }
        public String getCustomerRefId() { return this.customerRefId; }
        public String getResourceRefId() { return this.resourceRefId; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
        public void setFeatureRefId(String featureRefId) { this.featureRefId = featureRefId; }
        public void setCustomerRefId(String customerRefId) { this.customerRefId = customerRefId; }
        public void setResourceRefId(String resourceRefId) { this.resourceRefId = resourceRefId; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class MonthlyResetPeriodConfigInput {
        private MonthlyAccordingTo accordingTo;

        public MonthlyResetPeriodConfigInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("accordingTo") instanceof MonthlyAccordingTo) {
                    this.accordingTo = (MonthlyAccordingTo) args.get("accordingTo");
                } else {
                    this.accordingTo = MonthlyAccordingTo.valueOf((String) args.get("accordingTo"));
                }
            }
        }

        public MonthlyAccordingTo getAccordingTo() { return this.accordingTo; }
        public void setAccordingTo(MonthlyAccordingTo accordingTo) { this.accordingTo = accordingTo; }
    }
    public static class WeeklyResetPeriodConfigInput {
        private WeeklyAccordingTo accordingTo;

        public WeeklyResetPeriodConfigInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("accordingTo") instanceof WeeklyAccordingTo) {
                    this.accordingTo = (WeeklyAccordingTo) args.get("accordingTo");
                } else {
                    this.accordingTo = WeeklyAccordingTo.valueOf((String) args.get("accordingTo"));
                }
            }
        }

        public WeeklyAccordingTo getAccordingTo() { return this.accordingTo; }
        public void setAccordingTo(WeeklyAccordingTo accordingTo) { this.accordingTo = accordingTo; }
    }
    public static class UsageEventsInput {
        private Iterable<MeterFilterDefinitionInput> filters;
        private String environmentId;

        public UsageEventsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("filters") != null) {
                    this.filters = (Iterable<MeterFilterDefinitionInput>) args.get("filters");
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<MeterFilterDefinitionInput> getFilters() { return this.filters; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setFilters(Iterable<MeterFilterDefinitionInput> filters) { this.filters = filters; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class MeterFilterDefinitionInput {
        private Iterable<MeterConditionInput> conditions;

        public MeterFilterDefinitionInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("conditions") != null) {
                    this.conditions = (Iterable<MeterConditionInput>) args.get("conditions");
                }
            }
        }

        public Iterable<MeterConditionInput> getConditions() { return this.conditions; }
        public void setConditions(Iterable<MeterConditionInput> conditions) { this.conditions = conditions; }
    }
    public static class MeterConditionInput {
        private ConditionOperation operation;
        private String field;
        private String value;

        public MeterConditionInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("operation") instanceof ConditionOperation) {
                    this.operation = (ConditionOperation) args.get("operation");
                } else {
                    this.operation = ConditionOperation.valueOf((String) args.get("operation"));
                }
                this.field = (String) args.get("field");
                this.value = (String) args.get("value");
            }
        }

        public ConditionOperation getOperation() { return this.operation; }
        public String getField() { return this.field; }
        public String getValue() { return this.value; }
        public void setOperation(ConditionOperation operation) { this.operation = operation; }
        public void setField(String field) { this.field = field; }
        public void setValue(String value) { this.value = value; }
    }
    public static class AggregatedEventsByCustomerInput {
        private Iterable<MeterFilterDefinitionInput> filters;
        private MeterAggregationInput aggregation;
        private String customerId;
        private String environmentId;

        public AggregatedEventsByCustomerInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("filters") != null) {
                    this.filters = (Iterable<MeterFilterDefinitionInput>) args.get("filters");
                }
                this.aggregation = new MeterAggregationInput((Map<String, Object>) args.get("aggregation"));
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<MeterFilterDefinitionInput> getFilters() { return this.filters; }
        public MeterAggregationInput getAggregation() { return this.aggregation; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setFilters(Iterable<MeterFilterDefinitionInput> filters) { this.filters = filters; }
        public void setAggregation(MeterAggregationInput aggregation) { this.aggregation = aggregation; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class MeterAggregationInput {
        private AggregationFunction function;
        private String field;

        public MeterAggregationInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("function") instanceof AggregationFunction) {
                    this.function = (AggregationFunction) args.get("function");
                } else {
                    this.function = AggregationFunction.valueOf((String) args.get("function"));
                }
                this.field = (String) args.get("field");
            }
        }

        public AggregationFunction getFunction() { return this.function; }
        public String getField() { return this.field; }
        public void setFunction(AggregationFunction function) { this.function = function; }
        public void setField(String field) { this.field = field; }
    }
    public static class EventsFieldsInput {
        private Iterable<MeterFilterDefinitionInput> filters;
        private String environmentId;

        public EventsFieldsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("filters") != null) {
                    this.filters = (Iterable<MeterFilterDefinitionInput>) args.get("filters");
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<MeterFilterDefinitionInput> getFilters() { return this.filters; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setFilters(Iterable<MeterFilterDefinitionInput> filters) { this.filters = filters; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class UsageMeasurementFilterInput {
        private Iterable<UsageMeasurementFilterInput> and;
        private Iterable<UsageMeasurementFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput environmentId;
        private DateFieldComparisonInput createdAt;
        private UsageMeasurementFilterCustomerFilterInput customer;
        private UsageMeasurementFilterFeatureFilterInput feature;

        public UsageMeasurementFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<UsageMeasurementFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<UsageMeasurementFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.customer = new UsageMeasurementFilterCustomerFilterInput((Map<String, Object>) args.get("customer"));
                this.feature = new UsageMeasurementFilterFeatureFilterInput((Map<String, Object>) args.get("feature"));
            }
        }

        public Iterable<UsageMeasurementFilterInput> getAnd() { return this.and; }
        public Iterable<UsageMeasurementFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public UsageMeasurementFilterCustomerFilterInput getCustomer() { return this.customer; }
        public UsageMeasurementFilterFeatureFilterInput getFeature() { return this.feature; }
        public void setAnd(Iterable<UsageMeasurementFilterInput> and) { this.and = and; }
        public void setOr(Iterable<UsageMeasurementFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setCustomer(UsageMeasurementFilterCustomerFilterInput customer) { this.customer = customer; }
        public void setFeature(UsageMeasurementFilterFeatureFilterInput feature) { this.feature = feature; }
    }
    public static class UsageMeasurementFilterCustomerFilterInput {
        private Iterable<UsageMeasurementFilterCustomerFilterInput> and;
        private Iterable<UsageMeasurementFilterCustomerFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput name;
        private StringFieldComparisonInput email;
        private StringFieldComparisonInput refId;
        private StringFieldComparisonInput customerId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private DateFieldComparisonInput deletedAt;
        private StringFieldComparisonInput environmentId;
        private StringFieldComparisonInput billingId;
        private StringFieldComparisonInput crmId;
        private StringFieldComparisonInput crmHubspotCompanyId;
        private StringFieldComparisonInput crmHubspotCompanyUrl;
        private CustomerSearchQueryFilterComparisonInput searchQuery;

        public UsageMeasurementFilterCustomerFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<UsageMeasurementFilterCustomerFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<UsageMeasurementFilterCustomerFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.name = new StringFieldComparisonInput((Map<String, Object>) args.get("name"));
                this.email = new StringFieldComparisonInput((Map<String, Object>) args.get("email"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.customerId = new StringFieldComparisonInput((Map<String, Object>) args.get("customerId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.deletedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("deletedAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
                this.billingId = new StringFieldComparisonInput((Map<String, Object>) args.get("billingId"));
                this.crmId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmId"));
                this.crmHubspotCompanyId = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyId"));
                this.crmHubspotCompanyUrl = new StringFieldComparisonInput((Map<String, Object>) args.get("crmHubspotCompanyUrl"));
                this.searchQuery = new CustomerSearchQueryFilterComparisonInput((Map<String, Object>) args.get("searchQuery"));
            }
        }

        public Iterable<UsageMeasurementFilterCustomerFilterInput> getAnd() { return this.and; }
        public Iterable<UsageMeasurementFilterCustomerFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getName() { return this.name; }
        public StringFieldComparisonInput getEmail() { return this.email; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public StringFieldComparisonInput getCustomerId() { return this.customerId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public DateFieldComparisonInput getDeletedAt() { return this.deletedAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public StringFieldComparisonInput getBillingId() { return this.billingId; }
        public StringFieldComparisonInput getCrmId() { return this.crmId; }
        public StringFieldComparisonInput getCrmHubspotCompanyId() { return this.crmHubspotCompanyId; }
        public StringFieldComparisonInput getCrmHubspotCompanyUrl() { return this.crmHubspotCompanyUrl; }
        public CustomerSearchQueryFilterComparisonInput getSearchQuery() { return this.searchQuery; }
        public void setAnd(Iterable<UsageMeasurementFilterCustomerFilterInput> and) { this.and = and; }
        public void setOr(Iterable<UsageMeasurementFilterCustomerFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setName(StringFieldComparisonInput name) { this.name = name; }
        public void setEmail(StringFieldComparisonInput email) { this.email = email; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCustomerId(StringFieldComparisonInput customerId) { this.customerId = customerId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDeletedAt(DateFieldComparisonInput deletedAt) { this.deletedAt = deletedAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
        public void setBillingId(StringFieldComparisonInput billingId) { this.billingId = billingId; }
        public void setCrmId(StringFieldComparisonInput crmId) { this.crmId = crmId; }
        public void setCrmHubspotCompanyId(StringFieldComparisonInput crmHubspotCompanyId) { this.crmHubspotCompanyId = crmHubspotCompanyId; }
        public void setCrmHubspotCompanyUrl(StringFieldComparisonInput crmHubspotCompanyUrl) { this.crmHubspotCompanyUrl = crmHubspotCompanyUrl; }
        public void setSearchQuery(CustomerSearchQueryFilterComparisonInput searchQuery) { this.searchQuery = searchQuery; }
    }
    public static class UsageMeasurementFilterFeatureFilterInput {
        private Iterable<UsageMeasurementFilterFeatureFilterInput> and;
        private Iterable<UsageMeasurementFilterFeatureFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput displayName;
        private StringFieldComparisonInput refId;
        private DateFieldComparisonInput createdAt;
        private DateFieldComparisonInput updatedAt;
        private StringFieldComparisonInput description;
        private FeatureTypeFilterComparisonInput featureType;
        private MeterTypeFilterComparisonInput meterType;
        private FeatureStatusFilterComparisonInput featureStatus;
        private StringFieldComparisonInput environmentId;

        public UsageMeasurementFilterFeatureFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<UsageMeasurementFilterFeatureFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<UsageMeasurementFilterFeatureFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.displayName = new StringFieldComparisonInput((Map<String, Object>) args.get("displayName"));
                this.refId = new StringFieldComparisonInput((Map<String, Object>) args.get("refId"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.updatedAt = new DateFieldComparisonInput((Map<String, Object>) args.get("updatedAt"));
                this.description = new StringFieldComparisonInput((Map<String, Object>) args.get("description"));
                this.featureType = new FeatureTypeFilterComparisonInput((Map<String, Object>) args.get("featureType"));
                this.meterType = new MeterTypeFilterComparisonInput((Map<String, Object>) args.get("meterType"));
                this.featureStatus = new FeatureStatusFilterComparisonInput((Map<String, Object>) args.get("featureStatus"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<UsageMeasurementFilterFeatureFilterInput> getAnd() { return this.and; }
        public Iterable<UsageMeasurementFilterFeatureFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getDisplayName() { return this.displayName; }
        public StringFieldComparisonInput getRefId() { return this.refId; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public DateFieldComparisonInput getUpdatedAt() { return this.updatedAt; }
        public StringFieldComparisonInput getDescription() { return this.description; }
        public FeatureTypeFilterComparisonInput getFeatureType() { return this.featureType; }
        public MeterTypeFilterComparisonInput getMeterType() { return this.meterType; }
        public FeatureStatusFilterComparisonInput getFeatureStatus() { return this.featureStatus; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<UsageMeasurementFilterFeatureFilterInput> and) { this.and = and; }
        public void setOr(Iterable<UsageMeasurementFilterFeatureFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setDisplayName(StringFieldComparisonInput displayName) { this.displayName = displayName; }
        public void setRefId(StringFieldComparisonInput refId) { this.refId = refId; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(DateFieldComparisonInput updatedAt) { this.updatedAt = updatedAt; }
        public void setDescription(StringFieldComparisonInput description) { this.description = description; }
        public void setFeatureType(FeatureTypeFilterComparisonInput featureType) { this.featureType = featureType; }
        public void setMeterType(MeterTypeFilterComparisonInput meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatusFilterComparisonInput featureStatus) { this.featureStatus = featureStatus; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class UsageMeasurementSortInput {
        private UsageMeasurementSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public UsageMeasurementSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof UsageMeasurementSortFields) {
                    this.field = (UsageMeasurementSortFields) args.get("field");
                } else {
                    this.field = UsageMeasurementSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public UsageMeasurementSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(UsageMeasurementSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum UsageMeasurementSortFields {
        id,
        environmentId,
        createdAt

    }

    public static class TestHookInput {
        private String environmentId;
        private String endpointUrl;
        private EventLogType hookEventType;

        public TestHookInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.endpointUrl = (String) args.get("endpointUrl");
                if (args.get("hookEventType") instanceof EventLogType) {
                    this.hookEventType = (EventLogType) args.get("hookEventType");
                } else {
                    this.hookEventType = EventLogType.valueOf((String) args.get("hookEventType"));
                }
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getEndpointUrl() { return this.endpointUrl; }
        public EventLogType getHookEventType() { return this.hookEventType; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setEndpointUrl(String endpointUrl) { this.endpointUrl = endpointUrl; }
        public void setHookEventType(EventLogType hookEventType) { this.hookEventType = hookEventType; }
    }
    public static class HookFilterInput {
        private Iterable<HookFilterInput> and;
        private Iterable<HookFilterInput> or;
        private StringFieldComparisonInput id;
        private StringFieldComparisonInput endpoint;
        private HookStatusFilterComparisonInput status;
        private DateFieldComparisonInput createdAt;
        private StringFieldComparisonInput environmentId;

        public HookFilterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("and") != null) {
                    this.and = (Iterable<HookFilterInput>) args.get("and");
                }
                if (args.get("or") != null) {
                    this.or = (Iterable<HookFilterInput>) args.get("or");
                }
                this.id = new StringFieldComparisonInput((Map<String, Object>) args.get("id"));
                this.endpoint = new StringFieldComparisonInput((Map<String, Object>) args.get("endpoint"));
                this.status = new HookStatusFilterComparisonInput((Map<String, Object>) args.get("status"));
                this.createdAt = new DateFieldComparisonInput((Map<String, Object>) args.get("createdAt"));
                this.environmentId = new StringFieldComparisonInput((Map<String, Object>) args.get("environmentId"));
            }
        }

        public Iterable<HookFilterInput> getAnd() { return this.and; }
        public Iterable<HookFilterInput> getOr() { return this.or; }
        public StringFieldComparisonInput getId() { return this.id; }
        public StringFieldComparisonInput getEndpoint() { return this.endpoint; }
        public HookStatusFilterComparisonInput getStatus() { return this.status; }
        public DateFieldComparisonInput getCreatedAt() { return this.createdAt; }
        public StringFieldComparisonInput getEnvironmentId() { return this.environmentId; }
        public void setAnd(Iterable<HookFilterInput> and) { this.and = and; }
        public void setOr(Iterable<HookFilterInput> or) { this.or = or; }
        public void setId(StringFieldComparisonInput id) { this.id = id; }
        public void setEndpoint(StringFieldComparisonInput endpoint) { this.endpoint = endpoint; }
        public void setStatus(HookStatusFilterComparisonInput status) { this.status = status; }
        public void setCreatedAt(DateFieldComparisonInput createdAt) { this.createdAt = createdAt; }
        public void setEnvironmentId(StringFieldComparisonInput environmentId) { this.environmentId = environmentId; }
    }
    public static class HookStatusFilterComparisonInput {
        private Boolean is;
        private Boolean isNot;
        private HookStatus eq;
        private HookStatus neq;
        private HookStatus gt;
        private HookStatus gte;
        private HookStatus lt;
        private HookStatus lte;
        private HookStatus like;
        private HookStatus notLike;
        private HookStatus iLike;
        private HookStatus notILike;
        private Iterable<HookStatus> in;
        private Iterable<HookStatus> notIn;

        public HookStatusFilterComparisonInput(Map<String, Object> args) {
            if (args != null) {
                this.is = (Boolean) args.get("is");
                this.isNot = (Boolean) args.get("isNot");
                if (args.get("eq") instanceof HookStatus) {
                    this.eq = (HookStatus) args.get("eq");
                } else {
                    this.eq = HookStatus.valueOf((String) args.get("eq"));
                }
                if (args.get("neq") instanceof HookStatus) {
                    this.neq = (HookStatus) args.get("neq");
                } else {
                    this.neq = HookStatus.valueOf((String) args.get("neq"));
                }
                if (args.get("gt") instanceof HookStatus) {
                    this.gt = (HookStatus) args.get("gt");
                } else {
                    this.gt = HookStatus.valueOf((String) args.get("gt"));
                }
                if (args.get("gte") instanceof HookStatus) {
                    this.gte = (HookStatus) args.get("gte");
                } else {
                    this.gte = HookStatus.valueOf((String) args.get("gte"));
                }
                if (args.get("lt") instanceof HookStatus) {
                    this.lt = (HookStatus) args.get("lt");
                } else {
                    this.lt = HookStatus.valueOf((String) args.get("lt"));
                }
                if (args.get("lte") instanceof HookStatus) {
                    this.lte = (HookStatus) args.get("lte");
                } else {
                    this.lte = HookStatus.valueOf((String) args.get("lte"));
                }
                if (args.get("like") instanceof HookStatus) {
                    this.like = (HookStatus) args.get("like");
                } else {
                    this.like = HookStatus.valueOf((String) args.get("like"));
                }
                if (args.get("notLike") instanceof HookStatus) {
                    this.notLike = (HookStatus) args.get("notLike");
                } else {
                    this.notLike = HookStatus.valueOf((String) args.get("notLike"));
                }
                if (args.get("iLike") instanceof HookStatus) {
                    this.iLike = (HookStatus) args.get("iLike");
                } else {
                    this.iLike = HookStatus.valueOf((String) args.get("iLike"));
                }
                if (args.get("notILike") instanceof HookStatus) {
                    this.notILike = (HookStatus) args.get("notILike");
                } else {
                    this.notILike = HookStatus.valueOf((String) args.get("notILike"));
                }
                if (args.get("in") != null) {
                    this.in = (Iterable<HookStatus>) args.get("in");
                }
                if (args.get("notIn") != null) {
                    this.notIn = (Iterable<HookStatus>) args.get("notIn");
                }
            }
        }

        public Boolean getIs() { return this.is; }
        public Boolean getIsNot() { return this.isNot; }
        public HookStatus getEq() { return this.eq; }
        public HookStatus getNeq() { return this.neq; }
        public HookStatus getGt() { return this.gt; }
        public HookStatus getGte() { return this.gte; }
        public HookStatus getLt() { return this.lt; }
        public HookStatus getLte() { return this.lte; }
        public HookStatus getLike() { return this.like; }
        public HookStatus getNotLike() { return this.notLike; }
        public HookStatus getILike() { return this.iLike; }
        public HookStatus getNotILike() { return this.notILike; }
        public Iterable<HookStatus> getIn() { return this.in; }
        public Iterable<HookStatus> getNotIn() { return this.notIn; }
        public void setIs(Boolean is) { this.is = is; }
        public void setIsNot(Boolean isNot) { this.isNot = isNot; }
        public void setEq(HookStatus eq) { this.eq = eq; }
        public void setNeq(HookStatus neq) { this.neq = neq; }
        public void setGt(HookStatus gt) { this.gt = gt; }
        public void setGte(HookStatus gte) { this.gte = gte; }
        public void setLt(HookStatus lt) { this.lt = lt; }
        public void setLte(HookStatus lte) { this.lte = lte; }
        public void setLike(HookStatus like) { this.like = like; }
        public void setNotLike(HookStatus notLike) { this.notLike = notLike; }
        public void setILike(HookStatus iLike) { this.iLike = iLike; }
        public void setNotILike(HookStatus notILike) { this.notILike = notILike; }
        public void setIn(Iterable<HookStatus> in) { this.in = in; }
        public void setNotIn(Iterable<HookStatus> notIn) { this.notIn = notIn; }
    }
    public static class HookSortInput {
        private HookSortFields field;
        private SortDirection direction;
        private SortNulls nulls;

        public HookSortInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("field") instanceof HookSortFields) {
                    this.field = (HookSortFields) args.get("field");
                } else {
                    this.field = HookSortFields.valueOf((String) args.get("field"));
                }
                if (args.get("direction") instanceof SortDirection) {
                    this.direction = (SortDirection) args.get("direction");
                } else {
                    this.direction = SortDirection.valueOf((String) args.get("direction"));
                }
                if (args.get("nulls") instanceof SortNulls) {
                    this.nulls = (SortNulls) args.get("nulls");
                } else {
                    this.nulls = SortNulls.valueOf((String) args.get("nulls"));
                }
            }
        }

        public HookSortFields getField() { return this.field; }
        public SortDirection getDirection() { return this.direction; }
        public SortNulls getNulls() { return this.nulls; }
        public void setField(HookSortFields field) { this.field = field; }
        public void setDirection(SortDirection direction) { this.direction = direction; }
        public void setNulls(SortNulls nulls) { this.nulls = nulls; }
    }
    public enum HookSortFields {
        id,
        endpoint,
        status,
        createdAt,
        environmentId

    }

    public static class CheckoutStateInput {
        private String customerId;
        private String planId;
        private String resourceId;
        private String billingCountryCode;

        public CheckoutStateInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.planId = (String) args.get("planId");
                this.resourceId = (String) args.get("resourceId");
                this.billingCountryCode = (String) args.get("billingCountryCode");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getPlanId() { return this.planId; }
        public String getResourceId() { return this.resourceId; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
    }
    public static class MutationInviteMembersArgs {
        private Iterable<String> invites;

        public MutationInviteMembersArgs(Map<String, Object> args) {
            if (args != null) {
                this.invites = (Iterable<String>) args.get("invites");
            }
        }

        public Iterable<String> getInvites() { return this.invites; }
        public void setInvites(Iterable<String> invites) { this.invites = invites; }
    }
    public static class MutationRemoveMemberArgs {
        private String memberId;

        public MutationRemoveMemberArgs(Map<String, Object> args) {
            if (args != null) {
                this.memberId = (String) args.get("memberId");
            }
        }

        public String getMemberId() { return this.memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }
    }
    public static class MutationHideGettingStartedPageArgs {
        private String memberId;

        public MutationHideGettingStartedPageArgs(Map<String, Object> args) {
            if (args != null) {
                this.memberId = (String) args.get("memberId");
            }
        }

        public String getMemberId() { return this.memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }
    }
    public static class MutationUpdateUserArgs {
        private UpdateUserInput input;

        public MutationUpdateUserArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateUserInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateUserInput getInput() { return this.input; }
        public void setInput(UpdateUserInput input) { this.input = input; }
    }
    public static class MutationReportEntitlementCheckRequestedArgs {
        private EntitlementCheckRequestedInput entitlementCheckRequested;

        public MutationReportEntitlementCheckRequestedArgs(Map<String, Object> args) {
            if (args != null) {
                this.entitlementCheckRequested = new EntitlementCheckRequestedInput((Map<String, Object>) args.get("entitlementCheckRequested"));
            }
        }

        public EntitlementCheckRequestedInput getEntitlementCheckRequested() { return this.entitlementCheckRequested; }
        public void setEntitlementCheckRequested(EntitlementCheckRequestedInput entitlementCheckRequested) { this.entitlementCheckRequested = entitlementCheckRequested; }
    }
    public static class MutationRecalculateEntitlementsArgs {
        private RecalculateEntitlementsInput input;

        public MutationRecalculateEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RecalculateEntitlementsInput((Map<String, Object>) args.get("input"));
            }
        }

        public RecalculateEntitlementsInput getInput() { return this.input; }
        public void setInput(RecalculateEntitlementsInput input) { this.input = input; }
    }
    public static class MutationResyncIntegrationArgs {
        private ResyncIntegrationInput input;

        public MutationResyncIntegrationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ResyncIntegrationInput((Map<String, Object>) args.get("input"));
            }
        }

        public ResyncIntegrationInput getInput() { return this.input; }
        public void setInput(ResyncIntegrationInput input) { this.input = input; }
    }
    public static class MutationTriggerImportCatalogArgs {
        private ImportIntegrationCatalogInput input;

        public MutationTriggerImportCatalogArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ImportIntegrationCatalogInput((Map<String, Object>) args.get("input"));
            }
        }

        public ImportIntegrationCatalogInput getInput() { return this.input; }
        public void setInput(ImportIntegrationCatalogInput input) { this.input = input; }
    }
    public static class MutationTriggerImportCustomersArgs {
        private ImportIntegrationCustomersInput input;

        public MutationTriggerImportCustomersArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ImportIntegrationCustomersInput((Map<String, Object>) args.get("input"));
            }
        }

        public ImportIntegrationCustomersInput getInput() { return this.input; }
        public void setInput(ImportIntegrationCustomersInput input) { this.input = input; }
    }
    public static class MutationUpdateAccountArgs {
        private UpdateAccountInput input;

        public MutationUpdateAccountArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateAccountInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateAccountInput getInput() { return this.input; }
        public void setInput(UpdateAccountInput input) { this.input = input; }
    }
    public static class MutationCreateAccountArgs {
        private String accountName;

        public MutationCreateAccountArgs(Map<String, Object> args) {
            if (args != null) {
                this.accountName = (String) args.get("accountName");
            }
        }

        public String getAccountName() { return this.accountName; }
        public void setAccountName(String accountName) { this.accountName = accountName; }
    }
    public static class MutationDeleteEnvironmentArgs {
        private String slug;

        public MutationDeleteEnvironmentArgs(Map<String, Object> args) {
            if (args != null) {
                this.slug = (String) args.get("slug");
            }
        }

        public String getSlug() { return this.slug; }
        public void setSlug(String slug) { this.slug = slug; }
    }
    public static class MutationCreateOneEnvironmentArgs {
        private CreateOneEnvironmentInput input;

        public MutationCreateOneEnvironmentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateOneEnvironmentInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateOneEnvironmentInput getInput() { return this.input; }
        public void setInput(CreateOneEnvironmentInput input) { this.input = input; }
    }
    public static class MutationProvisionSandboxArgs {
        private ProvisionSandboxInput input;

        public MutationProvisionSandboxArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ProvisionSandboxInput((Map<String, Object>) args.get("input"));
            }
        }

        public ProvisionSandboxInput getInput() { return this.input; }
        public void setInput(ProvisionSandboxInput input) { this.input = input; }
    }
    public static class MutationCreateOneProductArgs {
        private CreateOneProductInput input;

        public MutationCreateOneProductArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateOneProductInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateOneProductInput getInput() { return this.input; }
        public void setInput(CreateOneProductInput input) { this.input = input; }
    }
    public static class MutationUpdateOneProductArgs {
        private UpdateOneProductInput input;

        public MutationUpdateOneProductArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOneProductInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOneProductInput getInput() { return this.input; }
        public void setInput(UpdateOneProductInput input) { this.input = input; }
    }
    public static class MutationDeleteOneProductArgs {
        private DeleteOneProductInput input;

        public MutationDeleteOneProductArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOneProductInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOneProductInput getInput() { return this.input; }
        public void setInput(DeleteOneProductInput input) { this.input = input; }
    }
    public static class MutationUpdateOneEnvironmentArgs {
        private UpdateOneEnvironmentInput input;

        public MutationUpdateOneEnvironmentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOneEnvironmentInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOneEnvironmentInput getInput() { return this.input; }
        public void setInput(UpdateOneEnvironmentInput input) { this.input = input; }
    }
    public static class MutationDeleteOneEnvironmentArgs {
        private DeleteOneEnvironmentInput input;

        public MutationDeleteOneEnvironmentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOneEnvironmentInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOneEnvironmentInput getInput() { return this.input; }
        public void setInput(DeleteOneEnvironmentInput input) { this.input = input; }
    }
    public static class MutationCreateOneExperimentArgs {
        private CreateExperimentInput input;

        public MutationCreateOneExperimentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateExperimentInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateExperimentInput getInput() { return this.input; }
        public void setInput(CreateExperimentInput input) { this.input = input; }
    }
    public static class MutationUpdateOneExperimentArgs {
        private UpdateExperimentInput input;

        public MutationUpdateOneExperimentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateExperimentInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateExperimentInput getInput() { return this.input; }
        public void setInput(UpdateExperimentInput input) { this.input = input; }
    }
    public static class MutationStartExperimentArgs {
        private StartExperimentInput input;

        public MutationStartExperimentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new StartExperimentInput((Map<String, Object>) args.get("input"));
            }
        }

        public StartExperimentInput getInput() { return this.input; }
        public void setInput(StartExperimentInput input) { this.input = input; }
    }
    public static class MutationStopExperimentArgs {
        private StopExperimentInput input;

        public MutationStopExperimentArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new StopExperimentInput((Map<String, Object>) args.get("input"));
            }
        }

        public StopExperimentInput getInput() { return this.input; }
        public void setInput(StopExperimentInput input) { this.input = input; }
    }
    public static class MutationCreateOneCouponArgs {
        private CreateCouponInput input;

        public MutationCreateOneCouponArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateCouponInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateCouponInput getInput() { return this.input; }
        public void setInput(CreateCouponInput input) { this.input = input; }
    }
    public static class MutationUpdateOneCouponArgs {
        private UpdateCouponInput input;

        public MutationUpdateOneCouponArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateCouponInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateCouponInput getInput() { return this.input; }
        public void setInput(UpdateCouponInput input) { this.input = input; }
    }
    public static class MutationArchiveOneCouponArgs {
        private ArchiveCouponInput input;

        public MutationArchiveOneCouponArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ArchiveCouponInput((Map<String, Object>) args.get("input"));
            }
        }

        public ArchiveCouponInput getInput() { return this.input; }
        public void setInput(ArchiveCouponInput input) { this.input = input; }
    }
    public static class MutationCreateOneIntegrationArgs {
        private CreateOneIntegrationInput input;

        public MutationCreateOneIntegrationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateOneIntegrationInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateOneIntegrationInput getInput() { return this.input; }
        public void setInput(CreateOneIntegrationInput input) { this.input = input; }
    }
    public static class MutationUpdateOneIntegrationArgs {
        private UpdateOneIntegrationInput input;

        public MutationUpdateOneIntegrationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOneIntegrationInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOneIntegrationInput getInput() { return this.input; }
        public void setInput(UpdateOneIntegrationInput input) { this.input = input; }
    }
    public static class MutationDeleteOneIntegrationArgs {
        private DeleteOneIntegrationInput input;

        public MutationDeleteOneIntegrationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOneIntegrationInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOneIntegrationInput getInput() { return this.input; }
        public void setInput(DeleteOneIntegrationInput input) { this.input = input; }
    }
    public static class MutationCancelSubscriptionArgs {
        private SubscriptionCancellationInput input;

        public MutationCancelSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SubscriptionCancellationInput((Map<String, Object>) args.get("input"));
            }
        }

        public SubscriptionCancellationInput getInput() { return this.input; }
        public void setInput(SubscriptionCancellationInput input) { this.input = input; }
    }
    public static class MutationCreateSubscriptionArgs {
        private SubscriptionInput subscription;

        public MutationCreateSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.subscription = new SubscriptionInput((Map<String, Object>) args.get("subscription"));
            }
        }

        public SubscriptionInput getSubscription() { return this.subscription; }
        public void setSubscription(SubscriptionInput subscription) { this.subscription = subscription; }
    }
    public static class MutationProvisionSubscriptionArgs {
        private ProvisionSubscriptionInput input;

        public MutationProvisionSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ProvisionSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public ProvisionSubscriptionInput getInput() { return this.input; }
        public void setInput(ProvisionSubscriptionInput input) { this.input = input; }
    }
    public static class MutationProvisionSubscriptionV2Args {
        private ProvisionSubscriptionInput input;

        public MutationProvisionSubscriptionV2Args(Map<String, Object> args) {
            if (args != null) {
                this.input = new ProvisionSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public ProvisionSubscriptionInput getInput() { return this.input; }
        public void setInput(ProvisionSubscriptionInput input) { this.input = input; }
    }
    public static class MutationImportSubscriptionsBulkArgs {
        private ImportSubscriptionsBulkInput input;

        public MutationImportSubscriptionsBulkArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ImportSubscriptionsBulkInput((Map<String, Object>) args.get("input"));
            }
        }

        public ImportSubscriptionsBulkInput getInput() { return this.input; }
        public void setInput(ImportSubscriptionsBulkInput input) { this.input = input; }
    }
    public static class MutationMigrateSubscriptionToLatestArgs {
        private SubscriptionMigrationInput input;

        public MutationMigrateSubscriptionToLatestArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SubscriptionMigrationInput((Map<String, Object>) args.get("input"));
            }
        }

        public SubscriptionMigrationInput getInput() { return this.input; }
        public void setInput(SubscriptionMigrationInput input) { this.input = input; }
    }
    public static class MutationTransferSubscriptionArgs {
        private TransferSubscriptionInput input;

        public MutationTransferSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new TransferSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public TransferSubscriptionInput getInput() { return this.input; }
        public void setInput(TransferSubscriptionInput input) { this.input = input; }
    }
    public static class MutationUpdateOneSubscriptionArgs {
        private UpdateSubscriptionInput input;

        public MutationUpdateOneSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateSubscriptionInput getInput() { return this.input; }
        public void setInput(UpdateSubscriptionInput input) { this.input = input; }
    }
    public static class MutationEstimateSubscriptionArgs {
        private EstimateSubscriptionInput input;

        public MutationEstimateSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new EstimateSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public EstimateSubscriptionInput getInput() { return this.input; }
        public void setInput(EstimateSubscriptionInput input) { this.input = input; }
    }
    public static class MutationEstimateSubscriptionUpdateArgs {
        private EstimateSubscriptionUpdateInput input;

        public MutationEstimateSubscriptionUpdateArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new EstimateSubscriptionUpdateInput((Map<String, Object>) args.get("input"));
            }
        }

        public EstimateSubscriptionUpdateInput getInput() { return this.input; }
        public void setInput(EstimateSubscriptionUpdateInput input) { this.input = input; }
    }
    public static class MutationCancelScheduleArgs {
        private SubscriptionUpdateScheduleCancellationInput input;

        public MutationCancelScheduleArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SubscriptionUpdateScheduleCancellationInput((Map<String, Object>) args.get("input"));
            }
        }

        public SubscriptionUpdateScheduleCancellationInput getInput() { return this.input; }
        public void setInput(SubscriptionUpdateScheduleCancellationInput input) { this.input = input; }
    }
    public static class MutationSetExperimentOnCustomerSubscriptionArgs {
        private SetExperimentOnCustomerSubscriptionInput input;

        public MutationSetExperimentOnCustomerSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetExperimentOnCustomerSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetExperimentOnCustomerSubscriptionInput getInput() { return this.input; }
        public void setInput(SetExperimentOnCustomerSubscriptionInput input) { this.input = input; }
    }
    public static class MutationSetCouponOnCustomerSubscriptionArgs {
        private SetCouponOnCustomerSubscriptionInput input;

        public MutationSetCouponOnCustomerSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetCouponOnCustomerSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetCouponOnCustomerSubscriptionInput getInput() { return this.input; }
        public void setInput(SetCouponOnCustomerSubscriptionInput input) { this.input = input; }
    }
    public static class MutationRemoveExperimentFromCustomerSubscriptionArgs {
        private RemoveExperimentFromCustomerSubscriptionInput input;

        public MutationRemoveExperimentFromCustomerSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveExperimentFromCustomerSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveExperimentFromCustomerSubscriptionInput getInput() { return this.input; }
        public void setInput(RemoveExperimentFromCustomerSubscriptionInput input) { this.input = input; }
    }
    public static class MutationRemoveCouponFromCustomerSubscriptionArgs {
        private RemoveCouponFromCustomerSubscriptionInput input;

        public MutationRemoveCouponFromCustomerSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveCouponFromCustomerSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveCouponFromCustomerSubscriptionInput getInput() { return this.input; }
        public void setInput(RemoveCouponFromCustomerSubscriptionInput input) { this.input = input; }
    }
    public static class MutationCreateFeatureArgs {
        private FeatureInput input;

        public MutationCreateFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new FeatureInput((Map<String, Object>) args.get("input"));
            }
        }

        public FeatureInput getInput() { return this.input; }
        public void setInput(FeatureInput input) { this.input = input; }
    }
    public static class MutationUpdateFeatureArgs {
        private UpdateFeatureInput_1Input input;

        public MutationUpdateFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateFeatureInput_1Input((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateFeatureInput_1Input getInput() { return this.input; }
        public void setInput(UpdateFeatureInput_1Input input) { this.input = input; }
    }
    public static class MutationDeleteFeatureArgs {
        private DeleteFeatureInput input;

        public MutationDeleteFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteFeatureInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteFeatureInput getInput() { return this.input; }
        public void setInput(DeleteFeatureInput input) { this.input = input; }
    }
    public static class MutationDeleteOneFeatureArgs {
        private DeleteFeatureInput input;

        public MutationDeleteOneFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteFeatureInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteFeatureInput getInput() { return this.input; }
        public void setInput(DeleteFeatureInput input) { this.input = input; }
    }
    public static class MutationCreateOneFeatureArgs {
        private CreateOneFeatureInput input;

        public MutationCreateOneFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateOneFeatureInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateOneFeatureInput getInput() { return this.input; }
        public void setInput(CreateOneFeatureInput input) { this.input = input; }
    }
    public static class MutationUpdateOneFeatureArgs {
        private UpdateOneFeatureInput input;

        public MutationUpdateOneFeatureArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOneFeatureInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOneFeatureInput getInput() { return this.input; }
        public void setInput(UpdateOneFeatureInput input) { this.input = input; }
    }
    public static class MutationUpdateEntitlementsOrderArgs {
        private UpdatePackageEntitlementOrderInput input;

        public MutationUpdateEntitlementsOrderArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdatePackageEntitlementOrderInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdatePackageEntitlementOrderInput getInput() { return this.input; }
        public void setInput(UpdatePackageEntitlementOrderInput input) { this.input = input; }
    }
    public static class MutationGrantPromotionalEntitlementsArgs {
        private GrantPromotionalEntitlementsInput input;

        public MutationGrantPromotionalEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new GrantPromotionalEntitlementsInput((Map<String, Object>) args.get("input"));
            }
        }

        public GrantPromotionalEntitlementsInput getInput() { return this.input; }
        public void setInput(GrantPromotionalEntitlementsInput input) { this.input = input; }
    }
    public static class MutationRevokePromotionalEntitlementArgs {
        private RevokePromotionalEntitlementInput input;

        public MutationRevokePromotionalEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RevokePromotionalEntitlementInput((Map<String, Object>) args.get("input"));
            }
        }

        public RevokePromotionalEntitlementInput getInput() { return this.input; }
        public void setInput(RevokePromotionalEntitlementInput input) { this.input = input; }
    }
    public static class MutationCreateManyPackageEntitlementsArgs {
        private CreateManyPackageEntitlementsInput input;

        public MutationCreateManyPackageEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateManyPackageEntitlementsInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateManyPackageEntitlementsInput getInput() { return this.input; }
        public void setInput(CreateManyPackageEntitlementsInput input) { this.input = input; }
    }
    public static class MutationUpdateOnePackageEntitlementArgs {
        private UpdateOnePackageEntitlementInput input;

        public MutationUpdateOnePackageEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOnePackageEntitlementInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOnePackageEntitlementInput getInput() { return this.input; }
        public void setInput(UpdateOnePackageEntitlementInput input) { this.input = input; }
    }
    public static class MutationDeleteOnePackageEntitlementArgs {
        private DeleteOnePackageEntitlementInput input;

        public MutationDeleteOnePackageEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOnePackageEntitlementInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOnePackageEntitlementInput getInput() { return this.input; }
        public void setInput(DeleteOnePackageEntitlementInput input) { this.input = input; }
    }
    public static class MutationCreateManyPromotionalEntitlementsArgs {
        private CreateManyPromotionalEntitlementsInput input;

        public MutationCreateManyPromotionalEntitlementsArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateManyPromotionalEntitlementsInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateManyPromotionalEntitlementsInput getInput() { return this.input; }
        public void setInput(CreateManyPromotionalEntitlementsInput input) { this.input = input; }
    }
    public static class MutationUpdateOnePromotionalEntitlementArgs {
        private UpdateOnePromotionalEntitlementInput input;

        public MutationUpdateOnePromotionalEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOnePromotionalEntitlementInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOnePromotionalEntitlementInput getInput() { return this.input; }
        public void setInput(UpdateOnePromotionalEntitlementInput input) { this.input = input; }
    }
    public static class MutationDeleteOnePromotionalEntitlementArgs {
        private DeleteOnePromotionalEntitlementInput input;

        public MutationDeleteOnePromotionalEntitlementArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOnePromotionalEntitlementInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOnePromotionalEntitlementInput getInput() { return this.input; }
        public void setInput(DeleteOnePromotionalEntitlementInput input) { this.input = input; }
    }
    public static class MutationCreateOneCustomerArgs {
        private CustomerInput input;

        public MutationCreateOneCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public CustomerInput getInput() { return this.input; }
        public void setInput(CustomerInput input) { this.input = input; }
    }
    public static class MutationProvisionCustomerArgs {
        private ProvisionCustomerInput input;

        public MutationProvisionCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ProvisionCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public ProvisionCustomerInput getInput() { return this.input; }
        public void setInput(ProvisionCustomerInput input) { this.input = input; }
    }
    public static class MutationArchiveCustomerArgs {
        private ArchiveCustomerInput input;

        public MutationArchiveCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ArchiveCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public ArchiveCustomerInput getInput() { return this.input; }
        public void setInput(ArchiveCustomerInput input) { this.input = input; }
    }
    public static class MutationImportCustomersBulkArgs {
        private ImportCustomerBulkInput input;

        public MutationImportCustomersBulkArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ImportCustomerBulkInput((Map<String, Object>) args.get("input"));
            }
        }

        public ImportCustomerBulkInput getInput() { return this.input; }
        public void setInput(ImportCustomerBulkInput input) { this.input = input; }
    }
    public static class MutationImportOneCustomerArgs {
        private ImportCustomerInput input;

        public MutationImportOneCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ImportCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public ImportCustomerInput getInput() { return this.input; }
        public void setInput(ImportCustomerInput input) { this.input = input; }
    }
    public static class MutationUpdateOneCustomerArgs {
        private UpdateCustomerInput input;

        public MutationUpdateOneCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateCustomerInput getInput() { return this.input; }
        public void setInput(UpdateCustomerInput input) { this.input = input; }
    }
    public static class MutationInitAddStripeCustomerPaymentMethodArgs {
        private InitAddStripeCustomerPaymentMethodInput input;

        public MutationInitAddStripeCustomerPaymentMethodArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new InitAddStripeCustomerPaymentMethodInput((Map<String, Object>) args.get("input"));
            }
        }

        public InitAddStripeCustomerPaymentMethodInput getInput() { return this.input; }
        public void setInput(InitAddStripeCustomerPaymentMethodInput input) { this.input = input; }
    }
    public static class MutationAttachCustomerPaymentMethodArgs {
        private AttachCustomerPaymentMethodInput input;

        public MutationAttachCustomerPaymentMethodArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new AttachCustomerPaymentMethodInput((Map<String, Object>) args.get("input"));
            }
        }

        public AttachCustomerPaymentMethodInput getInput() { return this.input; }
        public void setInput(AttachCustomerPaymentMethodInput input) { this.input = input; }
    }
    public static class MutationSetWidgetConfigurationArgs {
        private WidgetConfigurationUpdateInput input;

        public MutationSetWidgetConfigurationArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new WidgetConfigurationUpdateInput((Map<String, Object>) args.get("input"));
            }
        }

        public WidgetConfigurationUpdateInput getInput() { return this.input; }
        public void setInput(WidgetConfigurationUpdateInput input) { this.input = input; }
    }
    public static class MutationSetCouponOnCustomerArgs {
        private SetCouponOnCustomerInput input;

        public MutationSetCouponOnCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetCouponOnCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetCouponOnCustomerInput getInput() { return this.input; }
        public void setInput(SetCouponOnCustomerInput input) { this.input = input; }
    }
    public static class MutationSetExperimentOnCustomerArgs {
        private SetExperimentOnCustomerInput input;

        public MutationSetExperimentOnCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetExperimentOnCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetExperimentOnCustomerInput getInput() { return this.input; }
        public void setInput(SetExperimentOnCustomerInput input) { this.input = input; }
    }
    public static class MutationRemoveCouponFromCustomerArgs {
        private RemoveCouponFromCustomerInput input;

        public MutationRemoveCouponFromCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveCouponFromCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveCouponFromCustomerInput getInput() { return this.input; }
        public void setInput(RemoveCouponFromCustomerInput input) { this.input = input; }
    }
    public static class MutationRemoveExperimentFromCustomerArgs {
        private RemoveExperimentFromCustomerInput input;

        public MutationRemoveExperimentFromCustomerArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveExperimentFromCustomerInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveExperimentFromCustomerInput getInput() { return this.input; }
        public void setInput(RemoveExperimentFromCustomerInput input) { this.input = input; }
    }
    public static class MutationCreateOnePlanArgs {
        private PlanCreateInput input;

        public MutationCreateOnePlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PlanCreateInput((Map<String, Object>) args.get("input"));
            }
        }

        public PlanCreateInput getInput() { return this.input; }
        public void setInput(PlanCreateInput input) { this.input = input; }
    }
    public static class MutationPublishPlanArgs {
        private PackagePublishInput input;

        public MutationPublishPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PackagePublishInput((Map<String, Object>) args.get("input"));
            }
        }

        public PackagePublishInput getInput() { return this.input; }
        public void setInput(PackagePublishInput input) { this.input = input; }
    }
    public static class MutationArchivePlanArgs {
        private ArchivePlanInput input;

        public MutationArchivePlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ArchivePlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public ArchivePlanInput getInput() { return this.input; }
        public void setInput(ArchivePlanInput input) { this.input = input; }
    }
    public static class MutationRemovePlanDraftArgs {
        private DiscardPackageDraftInput input;

        public MutationRemovePlanDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DiscardPackageDraftInput((Map<String, Object>) args.get("input"));
            }
        }

        public DiscardPackageDraftInput getInput() { return this.input; }
        public void setInput(DiscardPackageDraftInput input) { this.input = input; }
    }
    public static class MutationCreatePlanDraftArgs {
        private String id;

        public MutationCreatePlanDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class MutationCreateEmptyPlanDraftArgs {
        private String id;

        public MutationCreateEmptyPlanDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class MutationUpdateOnePlanArgs {
        private PlanUpdateInput input;

        public MutationUpdateOnePlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PlanUpdateInput((Map<String, Object>) args.get("input"));
            }
        }

        public PlanUpdateInput getInput() { return this.input; }
        public void setInput(PlanUpdateInput input) { this.input = input; }
    }
    public static class MutationCreateOneAddonArgs {
        private AddonCreateInput input;

        public MutationCreateOneAddonArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new AddonCreateInput((Map<String, Object>) args.get("input"));
            }
        }

        public AddonCreateInput getInput() { return this.input; }
        public void setInput(AddonCreateInput input) { this.input = input; }
    }
    public static class MutationRemoveAddonDraftArgs {
        private DiscardPackageDraftInput input;

        public MutationRemoveAddonDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DiscardPackageDraftInput((Map<String, Object>) args.get("input"));
            }
        }

        public DiscardPackageDraftInput getInput() { return this.input; }
        public void setInput(DiscardPackageDraftInput input) { this.input = input; }
    }
    public static class MutationUpdateOneAddonArgs {
        private AddonUpdateInput input;

        public MutationUpdateOneAddonArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new AddonUpdateInput((Map<String, Object>) args.get("input"));
            }
        }

        public AddonUpdateInput getInput() { return this.input; }
        public void setInput(AddonUpdateInput input) { this.input = input; }
    }
    public static class MutationCreateAddonDraftArgs {
        private String id;

        public MutationCreateAddonDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class MutationCreateEmptyAddonDraftArgs {
        private String id;

        public MutationCreateEmptyAddonDraftArgs(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class MutationPublishAddonArgs {
        private PackagePublishInput input;

        public MutationPublishAddonArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PackagePublishInput((Map<String, Object>) args.get("input"));
            }
        }

        public PackagePublishInput getInput() { return this.input; }
        public void setInput(PackagePublishInput input) { this.input = input; }
    }
    public static class MutationSetPackagePricingArgs {
        private PackagePricingInput input;

        public MutationSetPackagePricingArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PackagePricingInput((Map<String, Object>) args.get("input"));
            }
        }

        public PackagePricingInput getInput() { return this.input; }
        public void setInput(PackagePricingInput input) { this.input = input; }
    }
    public static class MutationSetBasePlanOnPlanArgs {
        private SetBasePlanOnPlanInput input;

        public MutationSetBasePlanOnPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetBasePlanOnPlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetBasePlanOnPlanInput getInput() { return this.input; }
        public void setInput(SetBasePlanOnPlanInput input) { this.input = input; }
    }
    public static class MutationAddCompatibleAddonsToPlanArgs {
        private AddCompatibleAddonsToPlanInput input;

        public MutationAddCompatibleAddonsToPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new AddCompatibleAddonsToPlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public AddCompatibleAddonsToPlanInput getInput() { return this.input; }
        public void setInput(AddCompatibleAddonsToPlanInput input) { this.input = input; }
    }
    public static class MutationSetCompatibleAddonsOnPlanArgs {
        private SetCompatibleAddonsOnPlanInput input;

        public MutationSetCompatibleAddonsOnPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SetCompatibleAddonsOnPlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public SetCompatibleAddonsOnPlanInput getInput() { return this.input; }
        public void setInput(SetCompatibleAddonsOnPlanInput input) { this.input = input; }
    }
    public static class MutationRemoveBasePlanFromPlanArgs {
        private RemoveBasePlanFromPlanInput input;

        public MutationRemoveBasePlanFromPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveBasePlanFromPlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveBasePlanFromPlanInput getInput() { return this.input; }
        public void setInput(RemoveBasePlanFromPlanInput input) { this.input = input; }
    }
    public static class MutationRemoveCompatibleAddonsFromPlanArgs {
        private RemoveCompatibleAddonsFromPlanInput input;

        public MutationRemoveCompatibleAddonsFromPlanArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new RemoveCompatibleAddonsFromPlanInput((Map<String, Object>) args.get("input"));
            }
        }

        public RemoveCompatibleAddonsFromPlanInput getInput() { return this.input; }
        public void setInput(RemoveCompatibleAddonsFromPlanInput input) { this.input = input; }
    }
    public static class MutationDeleteOneAddonArgs {
        private DeleteOneAddonInput input;

        public MutationDeleteOneAddonArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOneAddonInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOneAddonInput getInput() { return this.input; }
        public void setInput(DeleteOneAddonInput input) { this.input = input; }
    }
    public static class MutationDeleteOnePriceArgs {
        private DeleteOnePriceInput input;

        public MutationDeleteOnePriceArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOnePriceInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOnePriceInput getInput() { return this.input; }
        public void setInput(DeleteOnePriceInput input) { this.input = input; }
    }
    public static class MutationCreateUsageMeasurementArgs {
        private UsageMeasurementCreateInput usageMeasurement;

        public MutationCreateUsageMeasurementArgs(Map<String, Object> args) {
            if (args != null) {
                this.usageMeasurement = new UsageMeasurementCreateInput((Map<String, Object>) args.get("usageMeasurement"));
            }
        }

        public UsageMeasurementCreateInput getUsageMeasurement() { return this.usageMeasurement; }
        public void setUsageMeasurement(UsageMeasurementCreateInput usageMeasurement) { this.usageMeasurement = usageMeasurement; }
    }
    public static class MutationReportUsageArgs {
        private ReportUsageInput input;

        public MutationReportUsageArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ReportUsageInput((Map<String, Object>) args.get("input"));
            }
        }

        public ReportUsageInput getInput() { return this.input; }
        public void setInput(ReportUsageInput input) { this.input = input; }
    }
    public static class MutationReportEventArgs {
        private UsageEventsReportInput events;

        public MutationReportEventArgs(Map<String, Object> args) {
            if (args != null) {
                this.events = new UsageEventsReportInput((Map<String, Object>) args.get("events"));
            }
        }

        public UsageEventsReportInput getEvents() { return this.events; }
        public void setEvents(UsageEventsReportInput events) { this.events = events; }
    }
    public static class MutationCreateOneHookArgs {
        private CreateOneHookInput input;

        public MutationCreateOneHookArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new CreateOneHookInput((Map<String, Object>) args.get("input"));
            }
        }

        public CreateOneHookInput getInput() { return this.input; }
        public void setInput(CreateOneHookInput input) { this.input = input; }
    }
    public static class MutationUpdateOneHookArgs {
        private UpdateOneHookInput input;

        public MutationUpdateOneHookArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new UpdateOneHookInput((Map<String, Object>) args.get("input"));
            }
        }

        public UpdateOneHookInput getInput() { return this.input; }
        public void setInput(UpdateOneHookInput input) { this.input = input; }
    }
    public static class MutationDeleteOneHookArgs {
        private DeleteOneHookInput input;

        public MutationDeleteOneHookArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new DeleteOneHookInput((Map<String, Object>) args.get("input"));
            }
        }

        public DeleteOneHookInput getInput() { return this.input; }
        public void setInput(DeleteOneHookInput input) { this.input = input; }
    }
    public static class MutationPurgeCustomerCacheArgs {
        private ClearCustomerPersistentCacheInput input;

        public MutationPurgeCustomerCacheArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ClearCustomerPersistentCacheInput((Map<String, Object>) args.get("input"));
            }
        }

        public ClearCustomerPersistentCacheInput getInput() { return this.input; }
        public void setInput(ClearCustomerPersistentCacheInput input) { this.input = input; }
    }
    public static class MutationApplySubscriptionArgs {
        private ApplySubscriptionInput input;

        public MutationApplySubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new ApplySubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public ApplySubscriptionInput getInput() { return this.input; }
        public void setInput(ApplySubscriptionInput input) { this.input = input; }
    }
    public static class MutationPreviewSubscriptionArgs {
        private PreviewSubscriptionInput input;

        public MutationPreviewSubscriptionArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new PreviewSubscriptionInput((Map<String, Object>) args.get("input"));
            }
        }

        public PreviewSubscriptionInput getInput() { return this.input; }
        public void setInput(PreviewSubscriptionInput input) { this.input = input; }
    }
    public static class MutationSyncTaxRatesArgs {
        private SyncTaxRatesInput input;

        public MutationSyncTaxRatesArgs(Map<String, Object> args) {
            if (args != null) {
                this.input = new SyncTaxRatesInput((Map<String, Object>) args.get("input"));
            }
        }

        public SyncTaxRatesInput getInput() { return this.input; }
        public void setInput(SyncTaxRatesInput input) { this.input = input; }
    }
    public static class UpdateUserInput {
        private String name;
        private Department department;

        public UpdateUserInput(Map<String, Object> args) {
            if (args != null) {
                this.name = (String) args.get("name");
                if (args.get("department") instanceof Department) {
                    this.department = (Department) args.get("department");
                } else {
                    this.department = Department.valueOf((String) args.get("department"));
                }
            }
        }

        public String getName() { return this.name; }
        public Department getDepartment() { return this.department; }
        public void setName(String name) { this.name = name; }
        public void setDepartment(Department department) { this.department = department; }
    }
    public static class EntitlementCheckRequestedInput {
        private String customerId;
        private String resourceId;
        private String featureId;
        private String environmentId;
        private Double requestedUsage;
        private EntitlementCheckResultInput entitlementCheckResult;

        public EntitlementCheckRequestedInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.featureId = (String) args.get("featureId");
                this.environmentId = (String) args.get("environmentId");
                this.requestedUsage = (Double) args.get("requestedUsage");
                this.entitlementCheckResult = new EntitlementCheckResultInput((Map<String, Object>) args.get("entitlementCheckResult"));
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getFeatureId() { return this.featureId; }
        public String getEnvironmentId() { return this.environmentId; }
        public Double getRequestedUsage() { return this.requestedUsage; }
        public EntitlementCheckResultInput getEntitlementCheckResult() { return this.entitlementCheckResult; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setRequestedUsage(Double requestedUsage) { this.requestedUsage = requestedUsage; }
        public void setEntitlementCheckResult(EntitlementCheckResultInput entitlementCheckResult) { this.entitlementCheckResult = entitlementCheckResult; }
    }
    public static class EntitlementCheckResultInput {
        private Boolean hasAccess;
        private AccessDeniedReason accessDeniedReason;
        private Double currentUsage;
        private Double requestedUsage;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Object nextResetDate;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;

        public EntitlementCheckResultInput(Map<String, Object> args) {
            if (args != null) {
                this.hasAccess = (Boolean) args.get("hasAccess");
                if (args.get("accessDeniedReason") instanceof AccessDeniedReason) {
                    this.accessDeniedReason = (AccessDeniedReason) args.get("accessDeniedReason");
                } else {
                    this.accessDeniedReason = AccessDeniedReason.valueOf((String) args.get("accessDeniedReason"));
                }
                this.currentUsage = (Double) args.get("currentUsage");
                this.requestedUsage = (Double) args.get("requestedUsage");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.nextResetDate = (Object) args.get("nextResetDate");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
            }
        }

        public Boolean getHasAccess() { return this.hasAccess; }
        public AccessDeniedReason getAccessDeniedReason() { return this.accessDeniedReason; }
        public Double getCurrentUsage() { return this.currentUsage; }
        public Double getRequestedUsage() { return this.requestedUsage; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Object getNextResetDate() { return this.nextResetDate; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public void setHasAccess(Boolean hasAccess) { this.hasAccess = hasAccess; }
        public void setAccessDeniedReason(AccessDeniedReason accessDeniedReason) { this.accessDeniedReason = accessDeniedReason; }
        public void setCurrentUsage(Double currentUsage) { this.currentUsage = currentUsage; }
        public void setRequestedUsage(Double requestedUsage) { this.requestedUsage = requestedUsage; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setNextResetDate(Object nextResetDate) { this.nextResetDate = nextResetDate; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
    }
    public static class RecalculateEntitlementsInput {
        private String environmentId;
        private Iterable<String> customerIds;
        private Boolean forAllCustomers;
        private Object lastCalculationBefore;

        public RecalculateEntitlementsInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.customerIds = (Iterable<String>) args.get("customerIds");
                this.forAllCustomers = (Boolean) args.get("forAllCustomers");
                this.lastCalculationBefore = (Object) args.get("lastCalculationBefore");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public Iterable<String> getCustomerIds() { return this.customerIds; }
        public Boolean getForAllCustomers() { return this.forAllCustomers; }
        public Object getLastCalculationBefore() { return this.lastCalculationBefore; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setCustomerIds(Iterable<String> customerIds) { this.customerIds = customerIds; }
        public void setForAllCustomers(Boolean forAllCustomers) { this.forAllCustomers = forAllCustomers; }
        public void setLastCalculationBefore(Object lastCalculationBefore) { this.lastCalculationBefore = lastCalculationBefore; }
    }
    public static class ResyncIntegrationInput {
        private String environmentId;
        private VendorIdentifier vendorIdentifier;

        public ResyncIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
    }
    public static class ImportIntegrationCatalogInput {
        private String environmentId;
        private VendorIdentifier vendorIdentifier;
        private String productId;
        private Iterable<String> selectedAddonBillingIds;
        private EntitySelectionMode entitySelectionMode;
        private Iterable<String> plansSelectionWhitelist;
        private Iterable<String> plansSelectionBlacklist;
        private BillingModel billingModel;
        private String featureUnitName;
        private String featureUnitPluralName;

        public ImportIntegrationCatalogInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
                this.productId = (String) args.get("productId");
                this.selectedAddonBillingIds = (Iterable<String>) args.get("selectedAddonBillingIds");
                if (args.get("entitySelectionMode") instanceof EntitySelectionMode) {
                    this.entitySelectionMode = (EntitySelectionMode) args.get("entitySelectionMode");
                } else {
                    this.entitySelectionMode = EntitySelectionMode.valueOf((String) args.get("entitySelectionMode"));
                }
                this.plansSelectionWhitelist = (Iterable<String>) args.get("plansSelectionWhitelist");
                this.plansSelectionBlacklist = (Iterable<String>) args.get("plansSelectionBlacklist");
                if (args.get("billingModel") instanceof BillingModel) {
                    this.billingModel = (BillingModel) args.get("billingModel");
                } else {
                    this.billingModel = BillingModel.valueOf((String) args.get("billingModel"));
                }
                this.featureUnitName = (String) args.get("featureUnitName");
                this.featureUnitPluralName = (String) args.get("featureUnitPluralName");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public String getProductId() { return this.productId; }
        public Iterable<String> getSelectedAddonBillingIds() { return this.selectedAddonBillingIds; }
        public EntitySelectionMode getEntitySelectionMode() { return this.entitySelectionMode; }
        public Iterable<String> getPlansSelectionWhitelist() { return this.plansSelectionWhitelist; }
        public Iterable<String> getPlansSelectionBlacklist() { return this.plansSelectionBlacklist; }
        public BillingModel getBillingModel() { return this.billingModel; }
        public String getFeatureUnitName() { return this.featureUnitName; }
        public String getFeatureUnitPluralName() { return this.featureUnitPluralName; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setSelectedAddonBillingIds(Iterable<String> selectedAddonBillingIds) { this.selectedAddonBillingIds = selectedAddonBillingIds; }
        public void setEntitySelectionMode(EntitySelectionMode entitySelectionMode) { this.entitySelectionMode = entitySelectionMode; }
        public void setPlansSelectionWhitelist(Iterable<String> plansSelectionWhitelist) { this.plansSelectionWhitelist = plansSelectionWhitelist; }
        public void setPlansSelectionBlacklist(Iterable<String> plansSelectionBlacklist) { this.plansSelectionBlacklist = plansSelectionBlacklist; }
        public void setBillingModel(BillingModel billingModel) { this.billingModel = billingModel; }
        public void setFeatureUnitName(String featureUnitName) { this.featureUnitName = featureUnitName; }
        public void setFeatureUnitPluralName(String featureUnitPluralName) { this.featureUnitPluralName = featureUnitPluralName; }
    }
    public enum EntitySelectionMode {
        BLACK_LIST,
        WHITE_LIST

    }

    public static class ImportIntegrationCustomersInput {
        private String environmentId;
        private VendorIdentifier vendorIdentifier;
        private String productId;
        private EntitySelectionMode entitySelectionMode;
        private Iterable<String> customersSelectionWhitelist;
        private Iterable<String> customersSelectionBlacklist;

        public ImportIntegrationCustomersInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
                this.productId = (String) args.get("productId");
                if (args.get("entitySelectionMode") instanceof EntitySelectionMode) {
                    this.entitySelectionMode = (EntitySelectionMode) args.get("entitySelectionMode");
                } else {
                    this.entitySelectionMode = EntitySelectionMode.valueOf((String) args.get("entitySelectionMode"));
                }
                this.customersSelectionWhitelist = (Iterable<String>) args.get("customersSelectionWhitelist");
                this.customersSelectionBlacklist = (Iterable<String>) args.get("customersSelectionBlacklist");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public String getProductId() { return this.productId; }
        public EntitySelectionMode getEntitySelectionMode() { return this.entitySelectionMode; }
        public Iterable<String> getCustomersSelectionWhitelist() { return this.customersSelectionWhitelist; }
        public Iterable<String> getCustomersSelectionBlacklist() { return this.customersSelectionBlacklist; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setEntitySelectionMode(EntitySelectionMode entitySelectionMode) { this.entitySelectionMode = entitySelectionMode; }
        public void setCustomersSelectionWhitelist(Iterable<String> customersSelectionWhitelist) { this.customersSelectionWhitelist = customersSelectionWhitelist; }
        public void setCustomersSelectionBlacklist(Iterable<String> customersSelectionBlacklist) { this.customersSelectionBlacklist = customersSelectionBlacklist; }
    }
    public static class UpdateAccountInput {
        private String id;
        private String displayName;
        private String timezone;
        private BillingAnchor subscriptionBillingAnchor;
        private ProrationBehavior subscriptionProrationBehavior;

        public UpdateAccountInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.displayName = (String) args.get("displayName");
                this.timezone = (String) args.get("timezone");
                if (args.get("subscriptionBillingAnchor") instanceof BillingAnchor) {
                    this.subscriptionBillingAnchor = (BillingAnchor) args.get("subscriptionBillingAnchor");
                } else {
                    this.subscriptionBillingAnchor = BillingAnchor.valueOf((String) args.get("subscriptionBillingAnchor"));
                }
                if (args.get("subscriptionProrationBehavior") instanceof ProrationBehavior) {
                    this.subscriptionProrationBehavior = (ProrationBehavior) args.get("subscriptionProrationBehavior");
                } else {
                    this.subscriptionProrationBehavior = ProrationBehavior.valueOf((String) args.get("subscriptionProrationBehavior"));
                }
            }
        }

        public String getId() { return this.id; }
        public String getDisplayName() { return this.displayName; }
        public String getTimezone() { return this.timezone; }
        public BillingAnchor getSubscriptionBillingAnchor() { return this.subscriptionBillingAnchor; }
        public ProrationBehavior getSubscriptionProrationBehavior() { return this.subscriptionProrationBehavior; }
        public void setId(String id) { this.id = id; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public void setSubscriptionBillingAnchor(BillingAnchor subscriptionBillingAnchor) { this.subscriptionBillingAnchor = subscriptionBillingAnchor; }
        public void setSubscriptionProrationBehavior(ProrationBehavior subscriptionProrationBehavior) { this.subscriptionProrationBehavior = subscriptionProrationBehavior; }
    }
    public static class CreateOneEnvironmentInput {
        private CreateEnvironmentInput environment;
        private CreateEnvironmentOptionsInput options;

        public CreateOneEnvironmentInput(Map<String, Object> args) {
            if (args != null) {
                this.environment = new CreateEnvironmentInput((Map<String, Object>) args.get("environment"));
                this.options = new CreateEnvironmentOptionsInput((Map<String, Object>) args.get("options"));
            }
        }

        public CreateEnvironmentInput getEnvironment() { return this.environment; }
        public CreateEnvironmentOptionsInput getOptions() { return this.options; }
        public void setEnvironment(CreateEnvironmentInput environment) { this.environment = environment; }
        public void setOptions(CreateEnvironmentOptionsInput options) { this.options = options; }
    }
    public static class CreateEnvironmentInput {
        private String id;
        private Object createdAt;
        private String displayName;
        private String description;
        private String slug;
        private EnvironmentProvisionStatus provisionStatus;
        private Boolean hardenClientAccessEnabled;
        private String color;

        public CreateEnvironmentInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.createdAt = (Object) args.get("createdAt");
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                this.slug = (String) args.get("slug");
                if (args.get("provisionStatus") instanceof EnvironmentProvisionStatus) {
                    this.provisionStatus = (EnvironmentProvisionStatus) args.get("provisionStatus");
                } else {
                    this.provisionStatus = EnvironmentProvisionStatus.valueOf((String) args.get("provisionStatus"));
                }
                this.hardenClientAccessEnabled = (Boolean) args.get("hardenClientAccessEnabled");
                this.color = (String) args.get("color");
            }
        }

        public String getId() { return this.id; }
        public Object getCreatedAt() { return this.createdAt; }
        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public String getSlug() { return this.slug; }
        public EnvironmentProvisionStatus getProvisionStatus() { return this.provisionStatus; }
        public Boolean getHardenClientAccessEnabled() { return this.hardenClientAccessEnabled; }
        public String getColor() { return this.color; }
        public void setId(String id) { this.id = id; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setSlug(String slug) { this.slug = slug; }
        public void setProvisionStatus(EnvironmentProvisionStatus provisionStatus) { this.provisionStatus = provisionStatus; }
        public void setHardenClientAccessEnabled(Boolean hardenClientAccessEnabled) { this.hardenClientAccessEnabled = hardenClientAccessEnabled; }
        public void setColor(String color) { this.color = color; }
    }
    public static class CreateEnvironmentOptionsInput {
        private Boolean createDefaultProduct;

        public CreateEnvironmentOptionsInput(Map<String, Object> args) {
            if (args != null) {
                this.createDefaultProduct = (Boolean) args.get("createDefaultProduct");
            }
        }

        public Boolean getCreateDefaultProduct() { return this.createDefaultProduct; }
        public void setCreateDefaultProduct(Boolean createDefaultProduct) { this.createDefaultProduct = createDefaultProduct; }
    }
    public static class ProvisionSandboxInput {
        private String displayName;
        private BillingModel billingModel;

        public ProvisionSandboxInput(Map<String, Object> args) {
            if (args != null) {
                this.displayName = (String) args.get("displayName");
                if (args.get("billingModel") instanceof BillingModel) {
                    this.billingModel = (BillingModel) args.get("billingModel");
                } else {
                    this.billingModel = BillingModel.valueOf((String) args.get("billingModel"));
                }
            }
        }

        public String getDisplayName() { return this.displayName; }
        public BillingModel getBillingModel() { return this.billingModel; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setBillingModel(BillingModel billingModel) { this.billingModel = billingModel; }
    }
    public static class CreateOneProductInput {
        private ProductCreateInput product;

        public CreateOneProductInput(Map<String, Object> args) {
            if (args != null) {
                this.product = new ProductCreateInput((Map<String, Object>) args.get("product"));
            }
        }

        public ProductCreateInput getProduct() { return this.product; }
        public void setProduct(ProductCreateInput product) { this.product = product; }
    }
    public static class ProductCreateInput {
        private String description;
        private String displayName;
        private String refId;
        private String environmentId;
        private Boolean multipleSubscriptions;
        private Object additionalMetaData;

        public ProductCreateInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.displayName = (String) args.get("displayName");
                this.refId = (String) args.get("refId");
                this.environmentId = (String) args.get("environmentId");
                this.multipleSubscriptions = (Boolean) args.get("multipleSubscriptions");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getDescription() { return this.description; }
        public String getDisplayName() { return this.displayName; }
        public String getRefId() { return this.refId; }
        public String getEnvironmentId() { return this.environmentId; }
        public Boolean getMultipleSubscriptions() { return this.multipleSubscriptions; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setDescription(String description) { this.description = description; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setMultipleSubscriptions(Boolean multipleSubscriptions) { this.multipleSubscriptions = multipleSubscriptions; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class UpdateOneProductInput {
        private String id;
        private ProductUpdateInput update;

        public UpdateOneProductInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new ProductUpdateInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public ProductUpdateInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(ProductUpdateInput update) { this.update = update; }
    }
    public static class ProductUpdateInput {
        private String description;
        private String displayName;
        private ProductSettingsInput productSettings;
        private Boolean multipleSubscriptions;
        private Object additionalMetaData;

        public ProductUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.displayName = (String) args.get("displayName");
                this.productSettings = new ProductSettingsInput((Map<String, Object>) args.get("productSettings"));
                this.multipleSubscriptions = (Boolean) args.get("multipleSubscriptions");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getDescription() { return this.description; }
        public String getDisplayName() { return this.displayName; }
        public ProductSettingsInput getProductSettings() { return this.productSettings; }
        public Boolean getMultipleSubscriptions() { return this.multipleSubscriptions; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setDescription(String description) { this.description = description; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setProductSettings(ProductSettingsInput productSettings) { this.productSettings = productSettings; }
        public void setMultipleSubscriptions(Boolean multipleSubscriptions) { this.multipleSubscriptions = multipleSubscriptions; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class ProductSettingsInput {
        private SubscriptionEndSetup subscriptionEndSetup;
        private SubscriptionCancellationTime subscriptionCancellationTime;
        private String downgradePlanId;
        private SubscriptionStartSetup subscriptionStartSetup;
        private String subscriptionStartPlanId;
        private String downgradeAtEndOfBillingPeriod;

        public ProductSettingsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("subscriptionEndSetup") instanceof SubscriptionEndSetup) {
                    this.subscriptionEndSetup = (SubscriptionEndSetup) args.get("subscriptionEndSetup");
                } else {
                    this.subscriptionEndSetup = SubscriptionEndSetup.valueOf((String) args.get("subscriptionEndSetup"));
                }
                if (args.get("subscriptionCancellationTime") instanceof SubscriptionCancellationTime) {
                    this.subscriptionCancellationTime = (SubscriptionCancellationTime) args.get("subscriptionCancellationTime");
                } else {
                    this.subscriptionCancellationTime = SubscriptionCancellationTime.valueOf((String) args.get("subscriptionCancellationTime"));
                }
                this.downgradePlanId = (String) args.get("downgradePlanId");
                if (args.get("subscriptionStartSetup") instanceof SubscriptionStartSetup) {
                    this.subscriptionStartSetup = (SubscriptionStartSetup) args.get("subscriptionStartSetup");
                } else {
                    this.subscriptionStartSetup = SubscriptionStartSetup.valueOf((String) args.get("subscriptionStartSetup"));
                }
                this.subscriptionStartPlanId = (String) args.get("subscriptionStartPlanId");
                this.downgradeAtEndOfBillingPeriod = (String) args.get("downgradeAtEndOfBillingPeriod");
            }
        }

        public SubscriptionEndSetup getSubscriptionEndSetup() { return this.subscriptionEndSetup; }
        public SubscriptionCancellationTime getSubscriptionCancellationTime() { return this.subscriptionCancellationTime; }
        public String getDowngradePlanId() { return this.downgradePlanId; }
        public SubscriptionStartSetup getSubscriptionStartSetup() { return this.subscriptionStartSetup; }
        public String getSubscriptionStartPlanId() { return this.subscriptionStartPlanId; }
        public String getDowngradeAtEndOfBillingPeriod() { return this.downgradeAtEndOfBillingPeriod; }
        public void setSubscriptionEndSetup(SubscriptionEndSetup subscriptionEndSetup) { this.subscriptionEndSetup = subscriptionEndSetup; }
        public void setSubscriptionCancellationTime(SubscriptionCancellationTime subscriptionCancellationTime) { this.subscriptionCancellationTime = subscriptionCancellationTime; }
        public void setDowngradePlanId(String downgradePlanId) { this.downgradePlanId = downgradePlanId; }
        public void setSubscriptionStartSetup(SubscriptionStartSetup subscriptionStartSetup) { this.subscriptionStartSetup = subscriptionStartSetup; }
        public void setSubscriptionStartPlanId(String subscriptionStartPlanId) { this.subscriptionStartPlanId = subscriptionStartPlanId; }
        public void setDowngradeAtEndOfBillingPeriod(String downgradeAtEndOfBillingPeriod) { this.downgradeAtEndOfBillingPeriod = downgradeAtEndOfBillingPeriod; }
    }
    public static class DeleteOneProductInput {
        private String id;

        public DeleteOneProductInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class UpdateOneEnvironmentInput {
        private String id;
        private EnvironmentInput update;

        public UpdateOneEnvironmentInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new EnvironmentInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public EnvironmentInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(EnvironmentInput update) { this.update = update; }
    }
    public static class EnvironmentInput {
        private String displayName;
        private String description;
        private String color;
        private EnvironmentProvisionStatus provisionStatus;
        private Boolean hardenClientAccessEnabled;

        public EnvironmentInput(Map<String, Object> args) {
            if (args != null) {
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                this.color = (String) args.get("color");
                if (args.get("provisionStatus") instanceof EnvironmentProvisionStatus) {
                    this.provisionStatus = (EnvironmentProvisionStatus) args.get("provisionStatus");
                } else {
                    this.provisionStatus = EnvironmentProvisionStatus.valueOf((String) args.get("provisionStatus"));
                }
                this.hardenClientAccessEnabled = (Boolean) args.get("hardenClientAccessEnabled");
            }
        }

        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public String getColor() { return this.color; }
        public EnvironmentProvisionStatus getProvisionStatus() { return this.provisionStatus; }
        public Boolean getHardenClientAccessEnabled() { return this.hardenClientAccessEnabled; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setColor(String color) { this.color = color; }
        public void setProvisionStatus(EnvironmentProvisionStatus provisionStatus) { this.provisionStatus = provisionStatus; }
        public void setHardenClientAccessEnabled(Boolean hardenClientAccessEnabled) { this.hardenClientAccessEnabled = hardenClientAccessEnabled; }
    }
    public static class DeleteOneEnvironmentInput {
        private String id;

        public DeleteOneEnvironmentInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class CreateExperimentInput {
        private String environmentId;
        private String description;
        private String name;
        private String productId;
        private Double variantPercentage;
        private String controlGroupName;
        private String variantGroupName;
        private ProductSettingsInput productSettings;

        public CreateExperimentInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.description = (String) args.get("description");
                this.name = (String) args.get("name");
                this.productId = (String) args.get("productId");
                this.variantPercentage = (Double) args.get("variantPercentage");
                this.controlGroupName = (String) args.get("controlGroupName");
                this.variantGroupName = (String) args.get("variantGroupName");
                this.productSettings = new ProductSettingsInput((Map<String, Object>) args.get("productSettings"));
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getDescription() { return this.description; }
        public String getName() { return this.name; }
        public String getProductId() { return this.productId; }
        public Double getVariantPercentage() { return this.variantPercentage; }
        public String getControlGroupName() { return this.controlGroupName; }
        public String getVariantGroupName() { return this.variantGroupName; }
        public ProductSettingsInput getProductSettings() { return this.productSettings; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setDescription(String description) { this.description = description; }
        public void setName(String name) { this.name = name; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setVariantPercentage(Double variantPercentage) { this.variantPercentage = variantPercentage; }
        public void setControlGroupName(String controlGroupName) { this.controlGroupName = controlGroupName; }
        public void setVariantGroupName(String variantGroupName) { this.variantGroupName = variantGroupName; }
        public void setProductSettings(ProductSettingsInput productSettings) { this.productSettings = productSettings; }
    }
    public static class UpdateExperimentInput {
        private String environmentId;
        private String refId;
        private String description;
        private String name;
        private String productId;
        private Double variantPercentage;
        private String controlGroupName;
        private String variantGroupName;
        private ProductSettingsInput productSettings;

        public UpdateExperimentInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.refId = (String) args.get("refId");
                this.description = (String) args.get("description");
                this.name = (String) args.get("name");
                this.productId = (String) args.get("productId");
                this.variantPercentage = (Double) args.get("variantPercentage");
                this.controlGroupName = (String) args.get("controlGroupName");
                this.variantGroupName = (String) args.get("variantGroupName");
                this.productSettings = new ProductSettingsInput((Map<String, Object>) args.get("productSettings"));
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getRefId() { return this.refId; }
        public String getDescription() { return this.description; }
        public String getName() { return this.name; }
        public String getProductId() { return this.productId; }
        public Double getVariantPercentage() { return this.variantPercentage; }
        public String getControlGroupName() { return this.controlGroupName; }
        public String getVariantGroupName() { return this.variantGroupName; }
        public ProductSettingsInput getProductSettings() { return this.productSettings; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setDescription(String description) { this.description = description; }
        public void setName(String name) { this.name = name; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setVariantPercentage(Double variantPercentage) { this.variantPercentage = variantPercentage; }
        public void setControlGroupName(String controlGroupName) { this.controlGroupName = controlGroupName; }
        public void setVariantGroupName(String variantGroupName) { this.variantGroupName = variantGroupName; }
        public void setProductSettings(ProductSettingsInput productSettings) { this.productSettings = productSettings; }
    }
    public static class StartExperimentInput {
        private String environmentId;
        private String refId;

        public StartExperimentInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.refId = (String) args.get("refId");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getRefId() { return this.refId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
    }
    public static class StopExperimentInput {
        private String environmentId;
        private String refId;

        public StopExperimentInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.refId = (String) args.get("refId");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getRefId() { return this.refId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
    }
    public static class CreateCouponInput {
        private String environmentId;
        private String refId;
        private String description;
        private String name;
        private CouponType type;
        private Double discountValue;
        private Object additionalMetaData;

        public CreateCouponInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.refId = (String) args.get("refId");
                this.description = (String) args.get("description");
                this.name = (String) args.get("name");
                if (args.get("type") instanceof CouponType) {
                    this.type = (CouponType) args.get("type");
                } else {
                    this.type = CouponType.valueOf((String) args.get("type"));
                }
                this.discountValue = (Double) args.get("discountValue");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getRefId() { return this.refId; }
        public String getDescription() { return this.description; }
        public String getName() { return this.name; }
        public CouponType getType() { return this.type; }
        public Double getDiscountValue() { return this.discountValue; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setDescription(String description) { this.description = description; }
        public void setName(String name) { this.name = name; }
        public void setType(CouponType type) { this.type = type; }
        public void setDiscountValue(Double discountValue) { this.discountValue = discountValue; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class UpdateCouponInput {
        private String refId;
        private String description;
        private String environmentId;
        private String name;
        private Object additionalMetaData;

        public UpdateCouponInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.description = (String) args.get("description");
                this.environmentId = (String) args.get("environmentId");
                this.name = (String) args.get("name");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getRefId() { return this.refId; }
        public String getDescription() { return this.description; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getName() { return this.name; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setDescription(String description) { this.description = description; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setName(String name) { this.name = name; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class ArchiveCouponInput {
        private String refId;
        private String environmentId;

        public ArchiveCouponInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getRefId() { return this.refId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class CreateOneIntegrationInput {
        private CreateIntegrationInput integration;

        public CreateOneIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                this.integration = new CreateIntegrationInput((Map<String, Object>) args.get("integration"));
            }
        }

        public CreateIntegrationInput getIntegration() { return this.integration; }
        public void setIntegration(CreateIntegrationInput integration) { this.integration = integration; }
    }
    public static class CreateIntegrationInput {
        private String environmentId;
        private VendorIdentifier vendorIdentifier;
        private ZuoraCredentialsInput zuoraCredentials;
        private StripeCredentialsInput stripeCredentials;
        private HubspotCredentialsInput hubspotCredentials;

        public CreateIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
                this.zuoraCredentials = new ZuoraCredentialsInput((Map<String, Object>) args.get("zuoraCredentials"));
                this.stripeCredentials = new StripeCredentialsInput((Map<String, Object>) args.get("stripeCredentials"));
                this.hubspotCredentials = new HubspotCredentialsInput((Map<String, Object>) args.get("hubspotCredentials"));
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public ZuoraCredentialsInput getZuoraCredentials() { return this.zuoraCredentials; }
        public StripeCredentialsInput getStripeCredentials() { return this.stripeCredentials; }
        public HubspotCredentialsInput getHubspotCredentials() { return this.hubspotCredentials; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
        public void setZuoraCredentials(ZuoraCredentialsInput zuoraCredentials) { this.zuoraCredentials = zuoraCredentials; }
        public void setStripeCredentials(StripeCredentialsInput stripeCredentials) { this.stripeCredentials = stripeCredentials; }
        public void setHubspotCredentials(HubspotCredentialsInput hubspotCredentials) { this.hubspotCredentials = hubspotCredentials; }
    }
    public static class ZuoraCredentialsInput {
        private String baseUrl;
        private String clientId;
        private String clientSecret;

        public ZuoraCredentialsInput(Map<String, Object> args) {
            if (args != null) {
                this.baseUrl = (String) args.get("baseUrl");
                this.clientId = (String) args.get("clientId");
                this.clientSecret = (String) args.get("clientSecret");
            }
        }

        public String getBaseUrl() { return this.baseUrl; }
        public String getClientId() { return this.clientId; }
        public String getClientSecret() { return this.clientSecret; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    }
    public static class StripeCredentialsInput {
        private String authorizationCode;
        private Boolean isTestMode;
        private String accountId;

        public StripeCredentialsInput(Map<String, Object> args) {
            if (args != null) {
                this.authorizationCode = (String) args.get("authorizationCode");
                this.isTestMode = (Boolean) args.get("isTestMode");
                this.accountId = (String) args.get("accountId");
            }
        }

        public String getAuthorizationCode() { return this.authorizationCode; }
        public Boolean getIsTestMode() { return this.isTestMode; }
        public String getAccountId() { return this.accountId; }
        public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
        public void setIsTestMode(Boolean isTestMode) { this.isTestMode = isTestMode; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
    }
    public static class HubspotCredentialsInput {
        private String authorizationCode;
        private String refreshToken;

        public HubspotCredentialsInput(Map<String, Object> args) {
            if (args != null) {
                this.authorizationCode = (String) args.get("authorizationCode");
                this.refreshToken = (String) args.get("refreshToken");
            }
        }

        public String getAuthorizationCode() { return this.authorizationCode; }
        public String getRefreshToken() { return this.refreshToken; }
        public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }
    public static class UpdateOneIntegrationInput {
        private String id;
        private UpdateIntegrationInput update;

        public UpdateOneIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new UpdateIntegrationInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public UpdateIntegrationInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(UpdateIntegrationInput update) { this.update = update; }
    }
    public static class UpdateIntegrationInput {
        private VendorIdentifier vendorIdentifier;
        private ZuoraCredentialsInput zuoraCredentials;
        private StripeCredentialsInput stripeCredentials;

        public UpdateIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
                this.zuoraCredentials = new ZuoraCredentialsInput((Map<String, Object>) args.get("zuoraCredentials"));
                this.stripeCredentials = new StripeCredentialsInput((Map<String, Object>) args.get("stripeCredentials"));
            }
        }

        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public ZuoraCredentialsInput getZuoraCredentials() { return this.zuoraCredentials; }
        public StripeCredentialsInput getStripeCredentials() { return this.stripeCredentials; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
        public void setZuoraCredentials(ZuoraCredentialsInput zuoraCredentials) { this.zuoraCredentials = zuoraCredentials; }
        public void setStripeCredentials(StripeCredentialsInput stripeCredentials) { this.stripeCredentials = stripeCredentials; }
    }
    public static class DeleteOneIntegrationInput {
        private String id;

        public DeleteOneIntegrationInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class SubscriptionCancellationInput {
        private String subscriptionRefId;
        private String environmentId;
        private SubscriptionCancellationTime subscriptionCancellationTime;
        private SubscriptionCancellationAction subscriptionCancellationAction;
        private Object endDate;

        public SubscriptionCancellationInput(Map<String, Object> args) {
            if (args != null) {
                this.subscriptionRefId = (String) args.get("subscriptionRefId");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("subscriptionCancellationTime") instanceof SubscriptionCancellationTime) {
                    this.subscriptionCancellationTime = (SubscriptionCancellationTime) args.get("subscriptionCancellationTime");
                } else {
                    this.subscriptionCancellationTime = SubscriptionCancellationTime.valueOf((String) args.get("subscriptionCancellationTime"));
                }
                if (args.get("subscriptionCancellationAction") instanceof SubscriptionCancellationAction) {
                    this.subscriptionCancellationAction = (SubscriptionCancellationAction) args.get("subscriptionCancellationAction");
                } else {
                    this.subscriptionCancellationAction = SubscriptionCancellationAction.valueOf((String) args.get("subscriptionCancellationAction"));
                }
                this.endDate = (Object) args.get("endDate");
            }
        }

        public String getSubscriptionRefId() { return this.subscriptionRefId; }
        public String getEnvironmentId() { return this.environmentId; }
        public SubscriptionCancellationTime getSubscriptionCancellationTime() { return this.subscriptionCancellationTime; }
        public SubscriptionCancellationAction getSubscriptionCancellationAction() { return this.subscriptionCancellationAction; }
        public Object getEndDate() { return this.endDate; }
        public void setSubscriptionRefId(String subscriptionRefId) { this.subscriptionRefId = subscriptionRefId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setSubscriptionCancellationTime(SubscriptionCancellationTime subscriptionCancellationTime) { this.subscriptionCancellationTime = subscriptionCancellationTime; }
        public void setSubscriptionCancellationAction(SubscriptionCancellationAction subscriptionCancellationAction) { this.subscriptionCancellationAction = subscriptionCancellationAction; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
    }
    /**  */
    public enum SubscriptionCancellationAction {
        DEFAULT,
        REVOKE_ENTITLEMENTS

    }

    public static class SubscriptionInput {
        private String planId;
        private BillingPeriod billingPeriod;
        private Double priceUnitAmount;
        private Double unitQuantity;
        private Iterable<SubscriptionAddonInput> addons;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Object startDate;
        private String refId;
        private String resourceId;
        private Object additionalMetaData;
        private Boolean awaitPaymentConfirmation;
        private SubscriptionBillingInfoInput billingInformation;
        private String billingId;
        private String subscriptionId;
        private String promotionCode;
        private String billingCountryCode;
        private Iterable<SubscriptionEntitlementInput> subscriptionEntitlements;
        private String customerId;
        private String crmId;
        private Boolean isTrial;
        private Boolean isOverridingTrialConfig;
        private Boolean isCustomPriceSubscription;
        private Object endDate;
        private String environmentId;

        public SubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.priceUnitAmount = (Double) args.get("priceUnitAmount");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                this.startDate = (Object) args.get("startDate");
                this.refId = (String) args.get("refId");
                this.resourceId = (String) args.get("resourceId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.awaitPaymentConfirmation = (Boolean) args.get("awaitPaymentConfirmation");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.billingId = (String) args.get("billingId");
                this.subscriptionId = (String) args.get("subscriptionId");
                this.promotionCode = (String) args.get("promotionCode");
                this.billingCountryCode = (String) args.get("billingCountryCode");
                if (args.get("subscriptionEntitlements") != null) {
                    this.subscriptionEntitlements = (Iterable<SubscriptionEntitlementInput>) args.get("subscriptionEntitlements");
                }
                this.customerId = (String) args.get("customerId");
                this.crmId = (String) args.get("crmId");
                this.isTrial = (Boolean) args.get("isTrial");
                this.isOverridingTrialConfig = (Boolean) args.get("isOverridingTrialConfig");
                this.isCustomPriceSubscription = (Boolean) args.get("isCustomPriceSubscription");
                this.endDate = (Object) args.get("endDate");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Double getPriceUnitAmount() { return this.priceUnitAmount; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Object getStartDate() { return this.startDate; }
        public String getRefId() { return this.refId; }
        public String getResourceId() { return this.resourceId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getAwaitPaymentConfirmation() { return this.awaitPaymentConfirmation; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getBillingId() { return this.billingId; }
        public String getSubscriptionId() { return this.subscriptionId; }
        public String getPromotionCode() { return this.promotionCode; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Iterable<SubscriptionEntitlementInput> getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public String getCustomerId() { return this.customerId; }
        public String getCrmId() { return this.crmId; }
        public Boolean getIsTrial() { return this.isTrial; }
        public Boolean getIsOverridingTrialConfig() { return this.isOverridingTrialConfig; }
        public Boolean getIsCustomPriceSubscription() { return this.isCustomPriceSubscription; }
        public Object getEndDate() { return this.endDate; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setPriceUnitAmount(Double priceUnitAmount) { this.priceUnitAmount = priceUnitAmount; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setAwaitPaymentConfirmation(Boolean awaitPaymentConfirmation) { this.awaitPaymentConfirmation = awaitPaymentConfirmation; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setSubscriptionEntitlements(Iterable<SubscriptionEntitlementInput> subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setCrmId(String crmId) { this.crmId = crmId; }
        public void setIsTrial(Boolean isTrial) { this.isTrial = isTrial; }
        public void setIsOverridingTrialConfig(Boolean isOverridingTrialConfig) { this.isOverridingTrialConfig = isOverridingTrialConfig; }
        public void setIsCustomPriceSubscription(Boolean isCustomPriceSubscription) { this.isCustomPriceSubscription = isCustomPriceSubscription; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class SubscriptionAddonInput {
        private String addonId;
        private Integer quantity;

        public SubscriptionAddonInput(Map<String, Object> args) {
            if (args != null) {
                this.addonId = (String) args.get("addonId");
                this.quantity = (Integer) args.get("quantity");
            }
        }

        public String getAddonId() { return this.addonId; }
        public Integer getQuantity() { return this.quantity; }
        public void setAddonId(String addonId) { this.addonId = addonId; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
    public static class BillableFeatureInput {
        private String featureId;
        private Double quantity;

        public BillableFeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.featureId = (String) args.get("featureId");
                this.quantity = (Double) args.get("quantity");
            }
        }

        public String getFeatureId() { return this.featureId; }
        public Double getQuantity() { return this.quantity; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
    }
    public static class SubscriptionBillingInfoInput {
        private Iterable<String> taxRateIds;
        private Double taxPercentage;
        private BillingAddressInput billingAddress;
        private Object metadata;

        public SubscriptionBillingInfoInput(Map<String, Object> args) {
            if (args != null) {
                this.taxRateIds = (Iterable<String>) args.get("taxRateIds");
                this.taxPercentage = (Double) args.get("taxPercentage");
                this.billingAddress = new BillingAddressInput((Map<String, Object>) args.get("billingAddress"));
                this.metadata = (Object) args.get("metadata");
            }
        }

        public Iterable<String> getTaxRateIds() { return this.taxRateIds; }
        public Double getTaxPercentage() { return this.taxPercentage; }
        public BillingAddressInput getBillingAddress() { return this.billingAddress; }
        public Object getMetadata() { return this.metadata; }
        public void setTaxRateIds(Iterable<String> taxRateIds) { this.taxRateIds = taxRateIds; }
        public void setTaxPercentage(Double taxPercentage) { this.taxPercentage = taxPercentage; }
        public void setBillingAddress(BillingAddressInput billingAddress) { this.billingAddress = billingAddress; }
        public void setMetadata(Object metadata) { this.metadata = metadata; }
    }
    public static class BillingAddressInput {
        private String city;
        private String country;
        private String line1;
        private String line2;
        private String postalCode;
        private String state;

        public BillingAddressInput(Map<String, Object> args) {
            if (args != null) {
                this.city = (String) args.get("city");
                this.country = (String) args.get("country");
                this.line1 = (String) args.get("line1");
                this.line2 = (String) args.get("line2");
                this.postalCode = (String) args.get("postalCode");
                this.state = (String) args.get("state");
            }
        }

        public String getCity() { return this.city; }
        public String getCountry() { return this.country; }
        public String getLine1() { return this.line1; }
        public String getLine2() { return this.line2; }
        public String getPostalCode() { return this.postalCode; }
        public String getState() { return this.state; }
        public void setCity(String city) { this.city = city; }
        public void setCountry(String country) { this.country = country; }
        public void setLine1(String line1) { this.line1 = line1; }
        public void setLine2(String line2) { this.line2 = line2; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public void setState(String state) { this.state = state; }
    }
    public static class SubscriptionEntitlementInput {
        private String description;
        private String featureId;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;

        public SubscriptionEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.featureId = (String) args.get("featureId");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
            }
        }

        public String getDescription() { return this.description; }
        public String getFeatureId() { return this.featureId; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public void setDescription(String description) { this.description = description; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
    }
    public static class ProvisionSubscriptionInput {
        private String planId;
        private BillingPeriod billingPeriod;
        private Double priceUnitAmount;
        private Double unitQuantity;
        private Iterable<SubscriptionAddonInput> addons;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Object startDate;
        private String refId;
        private String resourceId;
        private Object additionalMetaData;
        private Boolean awaitPaymentConfirmation;
        private SubscriptionBillingInfoInput billingInformation;
        private String billingId;
        private String subscriptionId;
        private String promotionCode;
        private String billingCountryCode;
        private Iterable<SubscriptionEntitlementInput> subscriptionEntitlements;
        private String customerId;
        private Boolean skipTrial;
        private CheckoutOptionsInput checkoutOptions;

        public ProvisionSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.priceUnitAmount = (Double) args.get("priceUnitAmount");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                this.startDate = (Object) args.get("startDate");
                this.refId = (String) args.get("refId");
                this.resourceId = (String) args.get("resourceId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.awaitPaymentConfirmation = (Boolean) args.get("awaitPaymentConfirmation");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.billingId = (String) args.get("billingId");
                this.subscriptionId = (String) args.get("subscriptionId");
                this.promotionCode = (String) args.get("promotionCode");
                this.billingCountryCode = (String) args.get("billingCountryCode");
                if (args.get("subscriptionEntitlements") != null) {
                    this.subscriptionEntitlements = (Iterable<SubscriptionEntitlementInput>) args.get("subscriptionEntitlements");
                }
                this.customerId = (String) args.get("customerId");
                this.skipTrial = (Boolean) args.get("skipTrial");
                this.checkoutOptions = new CheckoutOptionsInput((Map<String, Object>) args.get("checkoutOptions"));
            }
        }

        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Double getPriceUnitAmount() { return this.priceUnitAmount; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Object getStartDate() { return this.startDate; }
        public String getRefId() { return this.refId; }
        public String getResourceId() { return this.resourceId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getAwaitPaymentConfirmation() { return this.awaitPaymentConfirmation; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getBillingId() { return this.billingId; }
        public String getSubscriptionId() { return this.subscriptionId; }
        public String getPromotionCode() { return this.promotionCode; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Iterable<SubscriptionEntitlementInput> getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public String getCustomerId() { return this.customerId; }
        public Boolean getSkipTrial() { return this.skipTrial; }
        public CheckoutOptionsInput getCheckoutOptions() { return this.checkoutOptions; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setPriceUnitAmount(Double priceUnitAmount) { this.priceUnitAmount = priceUnitAmount; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setAwaitPaymentConfirmation(Boolean awaitPaymentConfirmation) { this.awaitPaymentConfirmation = awaitPaymentConfirmation; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setSubscriptionEntitlements(Iterable<SubscriptionEntitlementInput> subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setSkipTrial(Boolean skipTrial) { this.skipTrial = skipTrial; }
        public void setCheckoutOptions(CheckoutOptionsInput checkoutOptions) { this.checkoutOptions = checkoutOptions; }
    }
    public static class CheckoutOptionsInput {
        private String successUrl;
        private String cancelUrl;
        private Boolean allowPromoCodes;
        private Boolean allowTaxIdCollection;
        private Boolean collectBillingAddress;
        private String referenceId;
        private Boolean collectPhoneNumber;

        public CheckoutOptionsInput(Map<String, Object> args) {
            if (args != null) {
                this.successUrl = (String) args.get("successUrl");
                this.cancelUrl = (String) args.get("cancelUrl");
                this.allowPromoCodes = (Boolean) args.get("allowPromoCodes");
                this.allowTaxIdCollection = (Boolean) args.get("allowTaxIdCollection");
                this.collectBillingAddress = (Boolean) args.get("collectBillingAddress");
                this.referenceId = (String) args.get("referenceId");
                this.collectPhoneNumber = (Boolean) args.get("collectPhoneNumber");
            }
        }

        public String getSuccessUrl() { return this.successUrl; }
        public String getCancelUrl() { return this.cancelUrl; }
        public Boolean getAllowPromoCodes() { return this.allowPromoCodes; }
        public Boolean getAllowTaxIdCollection() { return this.allowTaxIdCollection; }
        public Boolean getCollectBillingAddress() { return this.collectBillingAddress; }
        public String getReferenceId() { return this.referenceId; }
        public Boolean getCollectPhoneNumber() { return this.collectPhoneNumber; }
        public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }
        public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
        public void setAllowPromoCodes(Boolean allowPromoCodes) { this.allowPromoCodes = allowPromoCodes; }
        public void setAllowTaxIdCollection(Boolean allowTaxIdCollection) { this.allowTaxIdCollection = allowTaxIdCollection; }
        public void setCollectBillingAddress(Boolean collectBillingAddress) { this.collectBillingAddress = collectBillingAddress; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
        public void setCollectPhoneNumber(Boolean collectPhoneNumber) { this.collectPhoneNumber = collectPhoneNumber; }
    }
    public static class ImportSubscriptionsBulkInput {
        private Iterable<ImportSubscriptionInput> subscriptions;
        private String environmentId;

        public ImportSubscriptionsBulkInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("subscriptions") != null) {
                    this.subscriptions = (Iterable<ImportSubscriptionInput>) args.get("subscriptions");
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<ImportSubscriptionInput> getSubscriptions() { return this.subscriptions; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setSubscriptions(Iterable<ImportSubscriptionInput> subscriptions) { this.subscriptions = subscriptions; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class ImportSubscriptionInput {
        private String customerId;
        private String billingId;
        private String resourceId;
        private String planId;
        private BillingPeriod billingPeriod;
        private Double unitQuantity;
        private Object startDate;
        private Object additionalMetaData;

        public ImportSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.billingId = (String) args.get("billingId");
                this.resourceId = (String) args.get("resourceId");
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.unitQuantity = (Double) args.get("unitQuantity");
                this.startDate = (Object) args.get("startDate");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getBillingId() { return this.billingId; }
        public String getResourceId() { return this.resourceId; }
        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Object getStartDate() { return this.startDate; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class SubscriptionMigrationInput {
        private String subscriptionId;
        private String environmentId;
        private SubscriptionMigrationTime subscriptionMigrationTime;

        public SubscriptionMigrationInput(Map<String, Object> args) {
            if (args != null) {
                this.subscriptionId = (String) args.get("subscriptionId");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("subscriptionMigrationTime") instanceof SubscriptionMigrationTime) {
                    this.subscriptionMigrationTime = (SubscriptionMigrationTime) args.get("subscriptionMigrationTime");
                } else {
                    this.subscriptionMigrationTime = SubscriptionMigrationTime.valueOf((String) args.get("subscriptionMigrationTime"));
                }
            }
        }

        public String getSubscriptionId() { return this.subscriptionId; }
        public String getEnvironmentId() { return this.environmentId; }
        public SubscriptionMigrationTime getSubscriptionMigrationTime() { return this.subscriptionMigrationTime; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setSubscriptionMigrationTime(SubscriptionMigrationTime subscriptionMigrationTime) { this.subscriptionMigrationTime = subscriptionMigrationTime; }
    }
    /** Set non immediate cancellation time (atm supported only for stripe integration) */
    public enum SubscriptionMigrationTime {
        END_OF_BILLING_PERIOD,
        IMMEDIATE

    }

    public static class TransferSubscriptionInput {
        private String customerId;
        private String sourceResourceId;
        private String destinationResourceId;

        public TransferSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.sourceResourceId = (String) args.get("sourceResourceId");
                this.destinationResourceId = (String) args.get("destinationResourceId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getSourceResourceId() { return this.sourceResourceId; }
        public String getDestinationResourceId() { return this.destinationResourceId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setSourceResourceId(String sourceResourceId) { this.sourceResourceId = sourceResourceId; }
        public void setDestinationResourceId(String destinationResourceId) { this.destinationResourceId = destinationResourceId; }
    }
    public static class UpdateSubscriptionInput {
        private Object additionalMetaData;
        private String environmentId;
        private Double unitQuantity;
        private Iterable<BillableFeatureInput> billableFeatures;
        private String refId;
        private String subscriptionId;
        private Iterable<SubscriptionAddonInput> addons;
        private Object trialEndDate;
        private String promotionCode;
        private Iterable<UpdateSubscriptionEntitlementInput> subscriptionEntitlements;
        private BillingPeriod billingPeriod;
        private Boolean awaitPaymentConfirmation;
        private SubscriptionBillingInfoInput billingInformation;

        public UpdateSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.environmentId = (String) args.get("environmentId");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                this.refId = (String) args.get("refId");
                this.subscriptionId = (String) args.get("subscriptionId");
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                this.trialEndDate = (Object) args.get("trialEndDate");
                this.promotionCode = (String) args.get("promotionCode");
                if (args.get("subscriptionEntitlements") != null) {
                    this.subscriptionEntitlements = (Iterable<UpdateSubscriptionEntitlementInput>) args.get("subscriptionEntitlements");
                }
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.awaitPaymentConfirmation = (Boolean) args.get("awaitPaymentConfirmation");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
            }
        }

        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public String getEnvironmentId() { return this.environmentId; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public String getRefId() { return this.refId; }
        public String getSubscriptionId() { return this.subscriptionId; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Object getTrialEndDate() { return this.trialEndDate; }
        public String getPromotionCode() { return this.promotionCode; }
        public Iterable<UpdateSubscriptionEntitlementInput> getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Boolean getAwaitPaymentConfirmation() { return this.awaitPaymentConfirmation; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setTrialEndDate(Object trialEndDate) { this.trialEndDate = trialEndDate; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setSubscriptionEntitlements(Iterable<UpdateSubscriptionEntitlementInput> subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setAwaitPaymentConfirmation(Boolean awaitPaymentConfirmation) { this.awaitPaymentConfirmation = awaitPaymentConfirmation; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
    }
    public static class UpdateSubscriptionEntitlementInput {
        private String id;
        private String featureId;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;

        public UpdateSubscriptionEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.featureId = (String) args.get("featureId");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
            }
        }

        public String getId() { return this.id; }
        public String getFeatureId() { return this.featureId; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public void setId(String id) { this.id = id; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
    }
    public static class EstimateSubscriptionInput {
        private String environmentId;
        private String customerId;
        private String resourceId;
        private String planId;
        private BillingPeriod billingPeriod;
        private String billingCountryCode;
        private Double priceUnitAmount;
        private Double unitQuantity;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Iterable<SubscriptionAddonInput> addons;
        private Object startDate;
        private SubscriptionBillingInfoInput billingInformation;
        private String promotionCode;
        private Boolean skipTrial;

        public EstimateSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.billingCountryCode = (String) args.get("billingCountryCode");
                this.priceUnitAmount = (Double) args.get("priceUnitAmount");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                this.startDate = (Object) args.get("startDate");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.promotionCode = (String) args.get("promotionCode");
                this.skipTrial = (Boolean) args.get("skipTrial");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Double getPriceUnitAmount() { return this.priceUnitAmount; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Object getStartDate() { return this.startDate; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getPromotionCode() { return this.promotionCode; }
        public Boolean getSkipTrial() { return this.skipTrial; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setPriceUnitAmount(Double priceUnitAmount) { this.priceUnitAmount = priceUnitAmount; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setSkipTrial(Boolean skipTrial) { this.skipTrial = skipTrial; }
    }
    public static class EstimateSubscriptionUpdateInput {
        private String environmentId;
        private String subscriptionId;
        private Double unitQuantity;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Iterable<SubscriptionAddonInput> addons;
        private String promotionCode;

        public EstimateSubscriptionUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.subscriptionId = (String) args.get("subscriptionId");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                this.promotionCode = (String) args.get("promotionCode");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getSubscriptionId() { return this.subscriptionId; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public String getPromotionCode() { return this.promotionCode; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
    }
    public static class SubscriptionUpdateScheduleCancellationInput {
        private String subscriptionId;
        private String environmentId;
        private SubscriptionScheduleStatus status;

        public SubscriptionUpdateScheduleCancellationInput(Map<String, Object> args) {
            if (args != null) {
                this.subscriptionId = (String) args.get("subscriptionId");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("status") instanceof SubscriptionScheduleStatus) {
                    this.status = (SubscriptionScheduleStatus) args.get("status");
                } else {
                    this.status = SubscriptionScheduleStatus.valueOf((String) args.get("status"));
                }
            }
        }

        public String getSubscriptionId() { return this.subscriptionId; }
        public String getEnvironmentId() { return this.environmentId; }
        public SubscriptionScheduleStatus getStatus() { return this.status; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setStatus(SubscriptionScheduleStatus status) { this.status = status; }
    }
    public static class SetExperimentOnCustomerSubscriptionInput {
        private String id;
        private String relationId;

        public SetExperimentOnCustomerSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class SetCouponOnCustomerSubscriptionInput {
        private String id;
        private String relationId;

        public SetCouponOnCustomerSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class RemoveExperimentFromCustomerSubscriptionInput {
        private String id;
        private String relationId;

        public RemoveExperimentFromCustomerSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class RemoveCouponFromCustomerSubscriptionInput {
        private String id;
        private String relationId;

        public RemoveCouponFromCustomerSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class FeatureInput {
        private String displayName;
        private String refId;
        private String featureUnits;
        private String featureUnitsPlural;
        private String description;
        private FeatureType featureType;
        private MeterType meterType;
        private FeatureStatus featureStatus;
        private CreateMeterInput meter;
        private String environmentId;
        private Object additionalMetaData;

        public FeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.displayName = (String) args.get("displayName");
                this.refId = (String) args.get("refId");
                this.featureUnits = (String) args.get("featureUnits");
                this.featureUnitsPlural = (String) args.get("featureUnitsPlural");
                this.description = (String) args.get("description");
                if (args.get("featureType") instanceof FeatureType) {
                    this.featureType = (FeatureType) args.get("featureType");
                } else {
                    this.featureType = FeatureType.valueOf((String) args.get("featureType"));
                }
                if (args.get("meterType") instanceof MeterType) {
                    this.meterType = (MeterType) args.get("meterType");
                } else {
                    this.meterType = MeterType.valueOf((String) args.get("meterType"));
                }
                if (args.get("featureStatus") instanceof FeatureStatus) {
                    this.featureStatus = (FeatureStatus) args.get("featureStatus");
                } else {
                    this.featureStatus = FeatureStatus.valueOf((String) args.get("featureStatus"));
                }
                this.meter = new CreateMeterInput((Map<String, Object>) args.get("meter"));
                this.environmentId = (String) args.get("environmentId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getDisplayName() { return this.displayName; }
        public String getRefId() { return this.refId; }
        public String getFeatureUnits() { return this.featureUnits; }
        public String getFeatureUnitsPlural() { return this.featureUnitsPlural; }
        public String getDescription() { return this.description; }
        public FeatureType getFeatureType() { return this.featureType; }
        public MeterType getMeterType() { return this.meterType; }
        public FeatureStatus getFeatureStatus() { return this.featureStatus; }
        public CreateMeterInput getMeter() { return this.meter; }
        public String getEnvironmentId() { return this.environmentId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setFeatureUnits(String featureUnits) { this.featureUnits = featureUnits; }
        public void setFeatureUnitsPlural(String featureUnitsPlural) { this.featureUnitsPlural = featureUnitsPlural; }
        public void setDescription(String description) { this.description = description; }
        public void setFeatureType(FeatureType featureType) { this.featureType = featureType; }
        public void setMeterType(MeterType meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatus featureStatus) { this.featureStatus = featureStatus; }
        public void setMeter(CreateMeterInput meter) { this.meter = meter; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class CreateMeterInput {
        private Iterable<MeterFilterDefinitionInput> filters;
        private MeterAggregationInput aggregation;

        public CreateMeterInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("filters") != null) {
                    this.filters = (Iterable<MeterFilterDefinitionInput>) args.get("filters");
                }
                this.aggregation = new MeterAggregationInput((Map<String, Object>) args.get("aggregation"));
            }
        }

        public Iterable<MeterFilterDefinitionInput> getFilters() { return this.filters; }
        public MeterAggregationInput getAggregation() { return this.aggregation; }
        public void setFilters(Iterable<MeterFilterDefinitionInput> filters) { this.filters = filters; }
        public void setAggregation(MeterAggregationInput aggregation) { this.aggregation = aggregation; }
    }
    public static class UpdateFeatureInput_1Input {
        private String featureUnits;
        private String featureUnitsPlural;
        private String description;
        private CreateMeterInput meter;
        private String environmentId;
        private String displayName;
        private String refId;
        private Object additionalMetaData;

        public UpdateFeatureInput_1Input(Map<String, Object> args) {
            if (args != null) {
                this.featureUnits = (String) args.get("featureUnits");
                this.featureUnitsPlural = (String) args.get("featureUnitsPlural");
                this.description = (String) args.get("description");
                this.meter = new CreateMeterInput((Map<String, Object>) args.get("meter"));
                this.environmentId = (String) args.get("environmentId");
                this.displayName = (String) args.get("displayName");
                this.refId = (String) args.get("refId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getFeatureUnits() { return this.featureUnits; }
        public String getFeatureUnitsPlural() { return this.featureUnitsPlural; }
        public String getDescription() { return this.description; }
        public CreateMeterInput getMeter() { return this.meter; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getDisplayName() { return this.displayName; }
        public String getRefId() { return this.refId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setFeatureUnits(String featureUnits) { this.featureUnits = featureUnits; }
        public void setFeatureUnitsPlural(String featureUnitsPlural) { this.featureUnitsPlural = featureUnitsPlural; }
        public void setDescription(String description) { this.description = description; }
        public void setMeter(CreateMeterInput meter) { this.meter = meter; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class DeleteFeatureInput {
        private String id;
        private String environmentId;

        public DeleteFeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getId() { return this.id; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setId(String id) { this.id = id; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class CreateOneFeatureInput {
        private FeatureInput feature;

        public CreateOneFeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.feature = new FeatureInput((Map<String, Object>) args.get("feature"));
            }
        }

        public FeatureInput getFeature() { return this.feature; }
        public void setFeature(FeatureInput feature) { this.feature = feature; }
    }
    public static class UpdateOneFeatureInput {
        private String id;
        private UpdateFeatureInput update;

        public UpdateOneFeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new UpdateFeatureInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public UpdateFeatureInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(UpdateFeatureInput update) { this.update = update; }
    }
    public static class UpdateFeatureInput {
        private String id;
        private String displayName;
        private String refId;
        private Object createdAt;
        private Object updatedAt;
        private String featureUnits;
        private String featureUnitsPlural;
        private String description;
        private FeatureType featureType;
        private MeterType meterType;
        private FeatureStatus featureStatus;
        private String environmentId;
        private Object additionalMetaData;

        public UpdateFeatureInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.displayName = (String) args.get("displayName");
                this.refId = (String) args.get("refId");
                this.createdAt = (Object) args.get("createdAt");
                this.updatedAt = (Object) args.get("updatedAt");
                this.featureUnits = (String) args.get("featureUnits");
                this.featureUnitsPlural = (String) args.get("featureUnitsPlural");
                this.description = (String) args.get("description");
                if (args.get("featureType") instanceof FeatureType) {
                    this.featureType = (FeatureType) args.get("featureType");
                } else {
                    this.featureType = FeatureType.valueOf((String) args.get("featureType"));
                }
                if (args.get("meterType") instanceof MeterType) {
                    this.meterType = (MeterType) args.get("meterType");
                } else {
                    this.meterType = MeterType.valueOf((String) args.get("meterType"));
                }
                if (args.get("featureStatus") instanceof FeatureStatus) {
                    this.featureStatus = (FeatureStatus) args.get("featureStatus");
                } else {
                    this.featureStatus = FeatureStatus.valueOf((String) args.get("featureStatus"));
                }
                this.environmentId = (String) args.get("environmentId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getId() { return this.id; }
        public String getDisplayName() { return this.displayName; }
        public String getRefId() { return this.refId; }
        public Object getCreatedAt() { return this.createdAt; }
        public Object getUpdatedAt() { return this.updatedAt; }
        public String getFeatureUnits() { return this.featureUnits; }
        public String getFeatureUnitsPlural() { return this.featureUnitsPlural; }
        public String getDescription() { return this.description; }
        public FeatureType getFeatureType() { return this.featureType; }
        public MeterType getMeterType() { return this.meterType; }
        public FeatureStatus getFeatureStatus() { return this.featureStatus; }
        public String getEnvironmentId() { return this.environmentId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setId(String id) { this.id = id; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setUpdatedAt(Object updatedAt) { this.updatedAt = updatedAt; }
        public void setFeatureUnits(String featureUnits) { this.featureUnits = featureUnits; }
        public void setFeatureUnitsPlural(String featureUnitsPlural) { this.featureUnitsPlural = featureUnitsPlural; }
        public void setDescription(String description) { this.description = description; }
        public void setFeatureType(FeatureType featureType) { this.featureType = featureType; }
        public void setMeterType(MeterType meterType) { this.meterType = meterType; }
        public void setFeatureStatus(FeatureStatus featureStatus) { this.featureStatus = featureStatus; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class UpdatePackageEntitlementOrderInput {
        private String environmentId;
        private String packageId;
        private Iterable<UpdatePackageEntitlementOrderItemInput> entitlements;

        public UpdatePackageEntitlementOrderInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.packageId = (String) args.get("packageId");
                if (args.get("entitlements") != null) {
                    this.entitlements = (Iterable<UpdatePackageEntitlementOrderItemInput>) args.get("entitlements");
                }
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getPackageId() { return this.packageId; }
        public Iterable<UpdatePackageEntitlementOrderItemInput> getEntitlements() { return this.entitlements; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setPackageId(String packageId) { this.packageId = packageId; }
        public void setEntitlements(Iterable<UpdatePackageEntitlementOrderItemInput> entitlements) { this.entitlements = entitlements; }
    }
    public static class UpdatePackageEntitlementOrderItemInput {
        private String id;
        private Double order;

        public UpdatePackageEntitlementOrderItemInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.order = (Double) args.get("order");
            }
        }

        public String getId() { return this.id; }
        public Double getOrder() { return this.order; }
        public void setId(String id) { this.id = id; }
        public void setOrder(Double order) { this.order = order; }
    }
    public static class GrantPromotionalEntitlementsInput {
        private Iterable<GrantPromotionalEntitlementInput> promotionalEntitlements;
        private String customerId;
        private String environmentId;

        public GrantPromotionalEntitlementsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("promotionalEntitlements") != null) {
                    this.promotionalEntitlements = (Iterable<GrantPromotionalEntitlementInput>) args.get("promotionalEntitlements");
                }
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<GrantPromotionalEntitlementInput> getPromotionalEntitlements() { return this.promotionalEntitlements; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setPromotionalEntitlements(Iterable<GrantPromotionalEntitlementInput> promotionalEntitlements) { this.promotionalEntitlements = promotionalEntitlements; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class GrantPromotionalEntitlementInput {
        private String featureId;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Object customEndDate;
        private PromotionalEntitlementPeriod period;
        private Boolean isVisible;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private EntitlementResetPeriod resetPeriod;

        public GrantPromotionalEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.featureId = (String) args.get("featureId");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.customEndDate = (Object) args.get("customEndDate");
                if (args.get("period") instanceof PromotionalEntitlementPeriod) {
                    this.period = (PromotionalEntitlementPeriod) args.get("period");
                } else {
                    this.period = PromotionalEntitlementPeriod.valueOf((String) args.get("period"));
                }
                this.isVisible = (Boolean) args.get("isVisible");
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
            }
        }

        public String getFeatureId() { return this.featureId; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Object getCustomEndDate() { return this.customEndDate; }
        public PromotionalEntitlementPeriod getPeriod() { return this.period; }
        public Boolean getIsVisible() { return this.isVisible; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setCustomEndDate(Object customEndDate) { this.customEndDate = customEndDate; }
        public void setPeriod(PromotionalEntitlementPeriod period) { this.period = period; }
        public void setIsVisible(Boolean isVisible) { this.isVisible = isVisible; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
    }
    public static class RevokePromotionalEntitlementInput {
        private String customerId;
        private String featureId;
        private String environmentId;

        public RevokePromotionalEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.featureId = (String) args.get("featureId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getFeatureId() { return this.featureId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class CreateManyPackageEntitlementsInput {
        private Iterable<PackageEntitlementInput> packageEntitlements;

        public CreateManyPackageEntitlementsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("packageEntitlements") != null) {
                    this.packageEntitlements = (Iterable<PackageEntitlementInput>) args.get("packageEntitlements");
                }
            }
        }

        public Iterable<PackageEntitlementInput> getPackageEntitlements() { return this.packageEntitlements; }
        public void setPackageEntitlements(Iterable<PackageEntitlementInput> packageEntitlements) { this.packageEntitlements = packageEntitlements; }
    }
    public static class PackageEntitlementInput {
        private String description;
        private String featureId;
        private String packageId;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Boolean isCustom;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private String environmentId;
        private Double order;
        private Iterable<WidgetType> hiddenFromWidgets;
        private String displayNameOverride;

        public PackageEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.featureId = (String) args.get("featureId");
                this.packageId = (String) args.get("packageId");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.isCustom = (Boolean) args.get("isCustom");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                this.environmentId = (String) args.get("environmentId");
                this.order = (Double) args.get("order");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
                this.displayNameOverride = (String) args.get("displayNameOverride");
            }
        }

        public String getDescription() { return this.description; }
        public String getFeatureId() { return this.featureId; }
        public String getPackageId() { return this.packageId; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Boolean getIsCustom() { return this.isCustom; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public String getEnvironmentId() { return this.environmentId; }
        public Double getOrder() { return this.order; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public String getDisplayNameOverride() { return this.displayNameOverride; }
        public void setDescription(String description) { this.description = description; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setPackageId(String packageId) { this.packageId = packageId; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setIsCustom(Boolean isCustom) { this.isCustom = isCustom; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setOrder(Double order) { this.order = order; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
        public void setDisplayNameOverride(String displayNameOverride) { this.displayNameOverride = displayNameOverride; }
    }
    public static class UpdateOnePackageEntitlementInput {
        private String id;
        private PackageEntitlementUpdateInput update;

        public UpdateOnePackageEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new PackageEntitlementUpdateInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public PackageEntitlementUpdateInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(PackageEntitlementUpdateInput update) { this.update = update; }
    }
    public static class PackageEntitlementUpdateInput {
        private String description;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Boolean isCustom;
        private EntitlementResetPeriod resetPeriod;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private Double order;
        private Iterable<WidgetType> hiddenFromWidgets;
        private String displayNameOverride;

        public PackageEntitlementUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.isCustom = (Boolean) args.get("isCustom");
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                this.order = (Double) args.get("order");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
                this.displayNameOverride = (String) args.get("displayNameOverride");
            }
        }

        public String getDescription() { return this.description; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Boolean getIsCustom() { return this.isCustom; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public Double getOrder() { return this.order; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public String getDisplayNameOverride() { return this.displayNameOverride; }
        public void setDescription(String description) { this.description = description; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setIsCustom(Boolean isCustom) { this.isCustom = isCustom; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setOrder(Double order) { this.order = order; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
        public void setDisplayNameOverride(String displayNameOverride) { this.displayNameOverride = displayNameOverride; }
    }
    public static class DeleteOnePackageEntitlementInput {
        private String id;

        public DeleteOnePackageEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class CreateManyPromotionalEntitlementsInput {
        private Iterable<PromotionalEntitlementInput> promotionalEntitlements;

        public CreateManyPromotionalEntitlementsInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("promotionalEntitlements") != null) {
                    this.promotionalEntitlements = (Iterable<PromotionalEntitlementInput>) args.get("promotionalEntitlements");
                }
            }
        }

        public Iterable<PromotionalEntitlementInput> getPromotionalEntitlements() { return this.promotionalEntitlements; }
        public void setPromotionalEntitlements(Iterable<PromotionalEntitlementInput> promotionalEntitlements) { this.promotionalEntitlements = promotionalEntitlements; }
    }
    public static class PromotionalEntitlementInput {
        private String description;
        private String featureId;
        private String customerId;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Object endDate;
        private PromotionalEntitlementPeriod period;
        private Boolean isVisible;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private EntitlementResetPeriod resetPeriod;
        private String environmentId;

        public PromotionalEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.featureId = (String) args.get("featureId");
                this.customerId = (String) args.get("customerId");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.endDate = (Object) args.get("endDate");
                if (args.get("period") instanceof PromotionalEntitlementPeriod) {
                    this.period = (PromotionalEntitlementPeriod) args.get("period");
                } else {
                    this.period = PromotionalEntitlementPeriod.valueOf((String) args.get("period"));
                }
                this.isVisible = (Boolean) args.get("isVisible");
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getDescription() { return this.description; }
        public String getFeatureId() { return this.featureId; }
        public String getCustomerId() { return this.customerId; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Object getEndDate() { return this.endDate; }
        public PromotionalEntitlementPeriod getPeriod() { return this.period; }
        public Boolean getIsVisible() { return this.isVisible; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setDescription(String description) { this.description = description; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
        public void setPeriod(PromotionalEntitlementPeriod period) { this.period = period; }
        public void setIsVisible(Boolean isVisible) { this.isVisible = isVisible; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class UpdateOnePromotionalEntitlementInput {
        private String id;
        private PromotionalEntitlementUpdateInput update;

        public UpdateOnePromotionalEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new PromotionalEntitlementUpdateInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public PromotionalEntitlementUpdateInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(PromotionalEntitlementUpdateInput update) { this.update = update; }
    }
    public static class PromotionalEntitlementUpdateInput {
        private String description;
        private Double usageLimit;
        private Boolean hasUnlimitedUsage;
        private Object endDate;
        private PromotionalEntitlementPeriod period;
        private Boolean isVisible;
        private MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration;
        private WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration;
        private EntitlementResetPeriod resetPeriod;

        public PromotionalEntitlementUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.description = (String) args.get("description");
                this.usageLimit = (Double) args.get("usageLimit");
                this.hasUnlimitedUsage = (Boolean) args.get("hasUnlimitedUsage");
                this.endDate = (Object) args.get("endDate");
                if (args.get("period") instanceof PromotionalEntitlementPeriod) {
                    this.period = (PromotionalEntitlementPeriod) args.get("period");
                } else {
                    this.period = PromotionalEntitlementPeriod.valueOf((String) args.get("period"));
                }
                this.isVisible = (Boolean) args.get("isVisible");
                this.monthlyResetPeriodConfiguration = new MonthlyResetPeriodConfigInput((Map<String, Object>) args.get("monthlyResetPeriodConfiguration"));
                this.weeklyResetPeriodConfiguration = new WeeklyResetPeriodConfigInput((Map<String, Object>) args.get("weeklyResetPeriodConfiguration"));
                if (args.get("resetPeriod") instanceof EntitlementResetPeriod) {
                    this.resetPeriod = (EntitlementResetPeriod) args.get("resetPeriod");
                } else {
                    this.resetPeriod = EntitlementResetPeriod.valueOf((String) args.get("resetPeriod"));
                }
            }
        }

        public String getDescription() { return this.description; }
        public Double getUsageLimit() { return this.usageLimit; }
        public Boolean getHasUnlimitedUsage() { return this.hasUnlimitedUsage; }
        public Object getEndDate() { return this.endDate; }
        public PromotionalEntitlementPeriod getPeriod() { return this.period; }
        public Boolean getIsVisible() { return this.isVisible; }
        public MonthlyResetPeriodConfigInput getMonthlyResetPeriodConfiguration() { return this.monthlyResetPeriodConfiguration; }
        public WeeklyResetPeriodConfigInput getWeeklyResetPeriodConfiguration() { return this.weeklyResetPeriodConfiguration; }
        public EntitlementResetPeriod getResetPeriod() { return this.resetPeriod; }
        public void setDescription(String description) { this.description = description; }
        public void setUsageLimit(Double usageLimit) { this.usageLimit = usageLimit; }
        public void setHasUnlimitedUsage(Boolean hasUnlimitedUsage) { this.hasUnlimitedUsage = hasUnlimitedUsage; }
        public void setEndDate(Object endDate) { this.endDate = endDate; }
        public void setPeriod(PromotionalEntitlementPeriod period) { this.period = period; }
        public void setIsVisible(Boolean isVisible) { this.isVisible = isVisible; }
        public void setMonthlyResetPeriodConfiguration(MonthlyResetPeriodConfigInput monthlyResetPeriodConfiguration) { this.monthlyResetPeriodConfiguration = monthlyResetPeriodConfiguration; }
        public void setWeeklyResetPeriodConfiguration(WeeklyResetPeriodConfigInput weeklyResetPeriodConfiguration) { this.weeklyResetPeriodConfiguration = weeklyResetPeriodConfiguration; }
        public void setResetPeriod(EntitlementResetPeriod resetPeriod) { this.resetPeriod = resetPeriod; }
    }
    public static class DeleteOnePromotionalEntitlementInput {
        private String id;

        public DeleteOnePromotionalEntitlementInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class CustomerInput {
        private String name;
        private String email;
        private String refId;
        private String customerId;
        private String environmentId;
        private String billingId;
        private String crmId;
        private String couponRefId;
        private CustomerBillingInfoInput billingInformation;
        private Object additionalMetaData;
        private Boolean shouldSyncFree;
        private Object createdAt;

        public CustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.name = (String) args.get("name");
                this.email = (String) args.get("email");
                this.refId = (String) args.get("refId");
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
                this.billingId = (String) args.get("billingId");
                this.crmId = (String) args.get("crmId");
                this.couponRefId = (String) args.get("couponRefId");
                this.billingInformation = new CustomerBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.shouldSyncFree = (Boolean) args.get("shouldSyncFree");
                this.createdAt = (Object) args.get("createdAt");
            }
        }

        public String getName() { return this.name; }
        public String getEmail() { return this.email; }
        public String getRefId() { return this.refId; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getBillingId() { return this.billingId; }
        public String getCrmId() { return this.crmId; }
        public String getCouponRefId() { return this.couponRefId; }
        public CustomerBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getShouldSyncFree() { return this.shouldSyncFree; }
        public Object getCreatedAt() { return this.createdAt; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setCrmId(String crmId) { this.crmId = crmId; }
        public void setCouponRefId(String couponRefId) { this.couponRefId = couponRefId; }
        public void setBillingInformation(CustomerBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setShouldSyncFree(Boolean shouldSyncFree) { this.shouldSyncFree = shouldSyncFree; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
    }
    public static class CustomerBillingInfoInput {
        private AddressInput billingAddress;
        private AddressInput shippingAddress;
        private Currency currency;
        private Iterable<TaxExemptInput> taxIds;
        private Object invoiceCustomFields;
        private String paymentMethodId;
        private String timezone;
        private String language;
        private String customerName;
        private Object metadata;

        public CustomerBillingInfoInput(Map<String, Object> args) {
            if (args != null) {
                this.billingAddress = new AddressInput((Map<String, Object>) args.get("billingAddress"));
                this.shippingAddress = new AddressInput((Map<String, Object>) args.get("shippingAddress"));
                if (args.get("currency") instanceof Currency) {
                    this.currency = (Currency) args.get("currency");
                } else {
                    this.currency = Currency.valueOf((String) args.get("currency"));
                }
                if (args.get("taxIds") != null) {
                    this.taxIds = (Iterable<TaxExemptInput>) args.get("taxIds");
                }
                this.invoiceCustomFields = (Object) args.get("invoiceCustomFields");
                this.paymentMethodId = (String) args.get("paymentMethodId");
                this.timezone = (String) args.get("timezone");
                this.language = (String) args.get("language");
                this.customerName = (String) args.get("customerName");
                this.metadata = (Object) args.get("metadata");
            }
        }

        public AddressInput getBillingAddress() { return this.billingAddress; }
        public AddressInput getShippingAddress() { return this.shippingAddress; }
        public Currency getCurrency() { return this.currency; }
        public Iterable<TaxExemptInput> getTaxIds() { return this.taxIds; }
        public Object getInvoiceCustomFields() { return this.invoiceCustomFields; }
        public String getPaymentMethodId() { return this.paymentMethodId; }
        public String getTimezone() { return this.timezone; }
        public String getLanguage() { return this.language; }
        public String getCustomerName() { return this.customerName; }
        public Object getMetadata() { return this.metadata; }
        public void setBillingAddress(AddressInput billingAddress) { this.billingAddress = billingAddress; }
        public void setShippingAddress(AddressInput shippingAddress) { this.shippingAddress = shippingAddress; }
        public void setCurrency(Currency currency) { this.currency = currency; }
        public void setTaxIds(Iterable<TaxExemptInput> taxIds) { this.taxIds = taxIds; }
        public void setInvoiceCustomFields(Object invoiceCustomFields) { this.invoiceCustomFields = invoiceCustomFields; }
        public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
        public void setTimezone(String timezone) { this.timezone = timezone; }
        public void setLanguage(String language) { this.language = language; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        public void setMetadata(Object metadata) { this.metadata = metadata; }
    }
    public static class AddressInput {
        private String country;
        private String state;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String postalCode;
        private String phoneNumber;

        public AddressInput(Map<String, Object> args) {
            if (args != null) {
                this.country = (String) args.get("country");
                this.state = (String) args.get("state");
                this.addressLine1 = (String) args.get("addressLine1");
                this.addressLine2 = (String) args.get("addressLine2");
                this.city = (String) args.get("city");
                this.postalCode = (String) args.get("postalCode");
                this.phoneNumber = (String) args.get("phoneNumber");
            }
        }

        public String getCountry() { return this.country; }
        public String getState() { return this.state; }
        public String getAddressLine1() { return this.addressLine1; }
        public String getAddressLine2() { return this.addressLine2; }
        public String getCity() { return this.city; }
        public String getPostalCode() { return this.postalCode; }
        public String getPhoneNumber() { return this.phoneNumber; }
        public void setCountry(String country) { this.country = country; }
        public void setState(String state) { this.state = state; }
        public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
        public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }
        public void setCity(String city) { this.city = city; }
        public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    }
    public static class TaxExemptInput {
        private String type;
        private String value;

        public TaxExemptInput(Map<String, Object> args) {
            if (args != null) {
                this.type = (String) args.get("type");
                this.value = (String) args.get("value");
            }
        }

        public String getType() { return this.type; }
        public String getValue() { return this.value; }
        public void setType(String type) { this.type = type; }
        public void setValue(String value) { this.value = value; }
    }
    public static class ProvisionCustomerInput {
        private String name;
        private String email;
        private String refId;
        private String customerId;
        private String environmentId;
        private String billingId;
        private String crmId;
        private String couponRefId;
        private CustomerBillingInfoInput billingInformation;
        private Object additionalMetaData;
        private Boolean shouldSyncFree;
        private Object createdAt;
        private ProvisionCustomerSubscriptionInput subscriptionParams;
        private Boolean excludeFromExperiment;

        public ProvisionCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.name = (String) args.get("name");
                this.email = (String) args.get("email");
                this.refId = (String) args.get("refId");
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
                this.billingId = (String) args.get("billingId");
                this.crmId = (String) args.get("crmId");
                this.couponRefId = (String) args.get("couponRefId");
                this.billingInformation = new CustomerBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.shouldSyncFree = (Boolean) args.get("shouldSyncFree");
                this.createdAt = (Object) args.get("createdAt");
                this.subscriptionParams = new ProvisionCustomerSubscriptionInput((Map<String, Object>) args.get("subscriptionParams"));
                this.excludeFromExperiment = (Boolean) args.get("excludeFromExperiment");
            }
        }

        public String getName() { return this.name; }
        public String getEmail() { return this.email; }
        public String getRefId() { return this.refId; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getBillingId() { return this.billingId; }
        public String getCrmId() { return this.crmId; }
        public String getCouponRefId() { return this.couponRefId; }
        public CustomerBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getShouldSyncFree() { return this.shouldSyncFree; }
        public Object getCreatedAt() { return this.createdAt; }
        public ProvisionCustomerSubscriptionInput getSubscriptionParams() { return this.subscriptionParams; }
        public Boolean getExcludeFromExperiment() { return this.excludeFromExperiment; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setCrmId(String crmId) { this.crmId = crmId; }
        public void setCouponRefId(String couponRefId) { this.couponRefId = couponRefId; }
        public void setBillingInformation(CustomerBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setShouldSyncFree(Boolean shouldSyncFree) { this.shouldSyncFree = shouldSyncFree; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setSubscriptionParams(ProvisionCustomerSubscriptionInput subscriptionParams) { this.subscriptionParams = subscriptionParams; }
        public void setExcludeFromExperiment(Boolean excludeFromExperiment) { this.excludeFromExperiment = excludeFromExperiment; }
    }
    public static class ProvisionCustomerSubscriptionInput {
        private String planId;
        private BillingPeriod billingPeriod;
        private Double priceUnitAmount;
        private Double unitQuantity;
        private Iterable<SubscriptionAddonInput> addons;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Object startDate;
        private String refId;
        private String resourceId;
        private Object additionalMetaData;
        private Boolean awaitPaymentConfirmation;
        private SubscriptionBillingInfoInput billingInformation;
        private String billingId;
        private String subscriptionId;
        private String promotionCode;
        private String billingCountryCode;
        private Iterable<SubscriptionEntitlementInput> subscriptionEntitlements;

        public ProvisionCustomerSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.priceUnitAmount = (Double) args.get("priceUnitAmount");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                this.startDate = (Object) args.get("startDate");
                this.refId = (String) args.get("refId");
                this.resourceId = (String) args.get("resourceId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.awaitPaymentConfirmation = (Boolean) args.get("awaitPaymentConfirmation");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.billingId = (String) args.get("billingId");
                this.subscriptionId = (String) args.get("subscriptionId");
                this.promotionCode = (String) args.get("promotionCode");
                this.billingCountryCode = (String) args.get("billingCountryCode");
                if (args.get("subscriptionEntitlements") != null) {
                    this.subscriptionEntitlements = (Iterable<SubscriptionEntitlementInput>) args.get("subscriptionEntitlements");
                }
            }
        }

        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Double getPriceUnitAmount() { return this.priceUnitAmount; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Object getStartDate() { return this.startDate; }
        public String getRefId() { return this.refId; }
        public String getResourceId() { return this.resourceId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getAwaitPaymentConfirmation() { return this.awaitPaymentConfirmation; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getBillingId() { return this.billingId; }
        public String getSubscriptionId() { return this.subscriptionId; }
        public String getPromotionCode() { return this.promotionCode; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Iterable<SubscriptionEntitlementInput> getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setPriceUnitAmount(Double priceUnitAmount) { this.priceUnitAmount = priceUnitAmount; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setAwaitPaymentConfirmation(Boolean awaitPaymentConfirmation) { this.awaitPaymentConfirmation = awaitPaymentConfirmation; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setSubscriptionId(String subscriptionId) { this.subscriptionId = subscriptionId; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setSubscriptionEntitlements(Iterable<SubscriptionEntitlementInput> subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
    }
    public static class ArchiveCustomerInput {
        private String customerId;
        private String environmentId;

        public ArchiveCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class ImportCustomerBulkInput {
        private Iterable<ImportCustomerInput> customers;
        private String environmentId;

        public ImportCustomerBulkInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("customers") != null) {
                    this.customers = (Iterable<ImportCustomerInput>) args.get("customers");
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Iterable<ImportCustomerInput> getCustomers() { return this.customers; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomers(Iterable<ImportCustomerInput> customers) { this.customers = customers; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class ImportCustomerInput {
        private String name;
        private String email;
        private String refId;
        private String customerId;
        private String environmentId;
        private String billingId;
        private String paymentMethodId;
        private Object additionalMetaData;

        public ImportCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.name = (String) args.get("name");
                this.email = (String) args.get("email");
                this.refId = (String) args.get("refId");
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
                this.billingId = (String) args.get("billingId");
                this.paymentMethodId = (String) args.get("paymentMethodId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
            }
        }

        public String getName() { return this.name; }
        public String getEmail() { return this.email; }
        public String getRefId() { return this.refId; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getBillingId() { return this.billingId; }
        public String getPaymentMethodId() { return this.paymentMethodId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
    }
    public static class UpdateCustomerInput {
        private String refId;
        private String customerId;
        private String name;
        private String email;
        private String environmentId;
        private String billingId;
        private String crmId;
        private String couponRefId;
        private CustomerBillingInfoInput billingInformation;
        private Object additionalMetaData;
        private Boolean shouldWaitSync;

        public UpdateCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.customerId = (String) args.get("customerId");
                this.name = (String) args.get("name");
                this.email = (String) args.get("email");
                this.environmentId = (String) args.get("environmentId");
                this.billingId = (String) args.get("billingId");
                this.crmId = (String) args.get("crmId");
                this.couponRefId = (String) args.get("couponRefId");
                this.billingInformation = new CustomerBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.shouldWaitSync = (Boolean) args.get("shouldWaitSync");
            }
        }

        public String getRefId() { return this.refId; }
        public String getCustomerId() { return this.customerId; }
        public String getName() { return this.name; }
        public String getEmail() { return this.email; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getBillingId() { return this.billingId; }
        public String getCrmId() { return this.crmId; }
        public String getCouponRefId() { return this.couponRefId; }
        public CustomerBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Boolean getShouldWaitSync() { return this.shouldWaitSync; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setName(String name) { this.name = name; }
        public void setEmail(String email) { this.email = email; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setCrmId(String crmId) { this.crmId = crmId; }
        public void setCouponRefId(String couponRefId) { this.couponRefId = couponRefId; }
        public void setBillingInformation(CustomerBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setShouldWaitSync(Boolean shouldWaitSync) { this.shouldWaitSync = shouldWaitSync; }
    }
    public static class InitAddStripeCustomerPaymentMethodInput {
        private String customerRefId;
        private String environmentId;

        public InitAddStripeCustomerPaymentMethodInput(Map<String, Object> args) {
            if (args != null) {
                this.customerRefId = (String) args.get("customerRefId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerRefId() { return this.customerRefId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerRefId(String customerRefId) { this.customerRefId = customerRefId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class AttachCustomerPaymentMethodInput {
        private String refId;
        private String customerId;
        private String environmentId;
        private VendorIdentifier vendorIdentifier;
        private String paymentMethodId;

        public AttachCustomerPaymentMethodInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.customerId = (String) args.get("customerId");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("vendorIdentifier") instanceof VendorIdentifier) {
                    this.vendorIdentifier = (VendorIdentifier) args.get("vendorIdentifier");
                } else {
                    this.vendorIdentifier = VendorIdentifier.valueOf((String) args.get("vendorIdentifier"));
                }
                this.paymentMethodId = (String) args.get("paymentMethodId");
            }
        }

        public String getRefId() { return this.refId; }
        public String getCustomerId() { return this.customerId; }
        public String getEnvironmentId() { return this.environmentId; }
        public VendorIdentifier getVendorIdentifier() { return this.vendorIdentifier; }
        public String getPaymentMethodId() { return this.paymentMethodId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setVendorIdentifier(VendorIdentifier vendorIdentifier) { this.vendorIdentifier = vendorIdentifier; }
        public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    }
    public static class WidgetConfigurationUpdateInput {
        private String environmentId;
        private PaywallConfigurationInput paywallConfiguration;
        private CustomerPortalConfigurationInput customerPortalConfiguration;
        private CheckoutConfigurationInput checkoutConfiguration;

        public WidgetConfigurationUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.paywallConfiguration = new PaywallConfigurationInput((Map<String, Object>) args.get("paywallConfiguration"));
                this.customerPortalConfiguration = new CustomerPortalConfigurationInput((Map<String, Object>) args.get("customerPortalConfiguration"));
                this.checkoutConfiguration = new CheckoutConfigurationInput((Map<String, Object>) args.get("checkoutConfiguration"));
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public PaywallConfigurationInput getPaywallConfiguration() { return this.paywallConfiguration; }
        public CustomerPortalConfigurationInput getCustomerPortalConfiguration() { return this.customerPortalConfiguration; }
        public CheckoutConfigurationInput getCheckoutConfiguration() { return this.checkoutConfiguration; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setPaywallConfiguration(PaywallConfigurationInput paywallConfiguration) { this.paywallConfiguration = paywallConfiguration; }
        public void setCustomerPortalConfiguration(CustomerPortalConfigurationInput customerPortalConfiguration) { this.customerPortalConfiguration = customerPortalConfiguration; }
        public void setCheckoutConfiguration(CheckoutConfigurationInput checkoutConfiguration) { this.checkoutConfiguration = checkoutConfiguration; }
    }
    public static class PaywallConfigurationInput {
        private PaywallColorsPaletteInput palette;
        private TypographyConfigurationInput typography;
        private PaywallLayoutConfigurationInput layout;
        private String customCss;

        public PaywallConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                this.palette = new PaywallColorsPaletteInput((Map<String, Object>) args.get("palette"));
                this.typography = new TypographyConfigurationInput((Map<String, Object>) args.get("typography"));
                this.layout = new PaywallLayoutConfigurationInput((Map<String, Object>) args.get("layout"));
                this.customCss = (String) args.get("customCss");
            }
        }

        public PaywallColorsPaletteInput getPalette() { return this.palette; }
        public TypographyConfigurationInput getTypography() { return this.typography; }
        public PaywallLayoutConfigurationInput getLayout() { return this.layout; }
        public String getCustomCss() { return this.customCss; }
        public void setPalette(PaywallColorsPaletteInput palette) { this.palette = palette; }
        public void setTypography(TypographyConfigurationInput typography) { this.typography = typography; }
        public void setLayout(PaywallLayoutConfigurationInput layout) { this.layout = layout; }
        public void setCustomCss(String customCss) { this.customCss = customCss; }
    }
    public static class PaywallColorsPaletteInput {
        private String primary;
        private String textColor;
        private String backgroundColor;
        private String borderColor;
        private String currentPlanBackground;

        public PaywallColorsPaletteInput(Map<String, Object> args) {
            if (args != null) {
                this.primary = (String) args.get("primary");
                this.textColor = (String) args.get("textColor");
                this.backgroundColor = (String) args.get("backgroundColor");
                this.borderColor = (String) args.get("borderColor");
                this.currentPlanBackground = (String) args.get("currentPlanBackground");
            }
        }

        public String getPrimary() { return this.primary; }
        public String getTextColor() { return this.textColor; }
        public String getBackgroundColor() { return this.backgroundColor; }
        public String getBorderColor() { return this.borderColor; }
        public String getCurrentPlanBackground() { return this.currentPlanBackground; }
        public void setPrimary(String primary) { this.primary = primary; }
        public void setTextColor(String textColor) { this.textColor = textColor; }
        public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
        public void setBorderColor(String borderColor) { this.borderColor = borderColor; }
        public void setCurrentPlanBackground(String currentPlanBackground) { this.currentPlanBackground = currentPlanBackground; }
    }
    public static class TypographyConfigurationInput {
        private String fontFamily;
        private FontVariantInput h1;
        private FontVariantInput h2;
        private FontVariantInput h3;
        private FontVariantInput body;

        public TypographyConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                this.fontFamily = (String) args.get("fontFamily");
                this.h1 = new FontVariantInput((Map<String, Object>) args.get("h1"));
                this.h2 = new FontVariantInput((Map<String, Object>) args.get("h2"));
                this.h3 = new FontVariantInput((Map<String, Object>) args.get("h3"));
                this.body = new FontVariantInput((Map<String, Object>) args.get("body"));
            }
        }

        public String getFontFamily() { return this.fontFamily; }
        public FontVariantInput getH1() { return this.h1; }
        public FontVariantInput getH2() { return this.h2; }
        public FontVariantInput getH3() { return this.h3; }
        public FontVariantInput getBody() { return this.body; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
        public void setH1(FontVariantInput h1) { this.h1 = h1; }
        public void setH2(FontVariantInput h2) { this.h2 = h2; }
        public void setH3(FontVariantInput h3) { this.h3 = h3; }
        public void setBody(FontVariantInput body) { this.body = body; }
    }
    public static class FontVariantInput {
        private Double fontSize;
        private FontWeight fontWeight;

        public FontVariantInput(Map<String, Object> args) {
            if (args != null) {
                this.fontSize = (Double) args.get("fontSize");
                if (args.get("fontWeight") instanceof FontWeight) {
                    this.fontWeight = (FontWeight) args.get("fontWeight");
                } else {
                    this.fontWeight = FontWeight.valueOf((String) args.get("fontWeight"));
                }
            }
        }

        public Double getFontSize() { return this.fontSize; }
        public FontWeight getFontWeight() { return this.fontWeight; }
        public void setFontSize(Double fontSize) { this.fontSize = fontSize; }
        public void setFontWeight(FontWeight fontWeight) { this.fontWeight = fontWeight; }
    }
    public static class PaywallLayoutConfigurationInput {
        private Alignment alignment;
        private Double planWidth;
        private Double planMargin;
        private Double planPadding;

        public PaywallLayoutConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("alignment") instanceof Alignment) {
                    this.alignment = (Alignment) args.get("alignment");
                } else {
                    this.alignment = Alignment.valueOf((String) args.get("alignment"));
                }
                this.planWidth = (Double) args.get("planWidth");
                this.planMargin = (Double) args.get("planMargin");
                this.planPadding = (Double) args.get("planPadding");
            }
        }

        public Alignment getAlignment() { return this.alignment; }
        public Double getPlanWidth() { return this.planWidth; }
        public Double getPlanMargin() { return this.planMargin; }
        public Double getPlanPadding() { return this.planPadding; }
        public void setAlignment(Alignment alignment) { this.alignment = alignment; }
        public void setPlanWidth(Double planWidth) { this.planWidth = planWidth; }
        public void setPlanMargin(Double planMargin) { this.planMargin = planMargin; }
        public void setPlanPadding(Double planPadding) { this.planPadding = planPadding; }
    }
    public static class CustomerPortalConfigurationInput {
        private CustomerPortalColorsPaletteInput palette;
        private TypographyConfigurationInput typography;
        private String customCss;

        public CustomerPortalConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                this.palette = new CustomerPortalColorsPaletteInput((Map<String, Object>) args.get("palette"));
                this.typography = new TypographyConfigurationInput((Map<String, Object>) args.get("typography"));
                this.customCss = (String) args.get("customCss");
            }
        }

        public CustomerPortalColorsPaletteInput getPalette() { return this.palette; }
        public TypographyConfigurationInput getTypography() { return this.typography; }
        public String getCustomCss() { return this.customCss; }
        public void setPalette(CustomerPortalColorsPaletteInput palette) { this.palette = palette; }
        public void setTypography(TypographyConfigurationInput typography) { this.typography = typography; }
        public void setCustomCss(String customCss) { this.customCss = customCss; }
    }
    public static class CustomerPortalColorsPaletteInput {
        private String primary;
        private String textColor;
        private String backgroundColor;
        private String borderColor;
        private String currentPlanBackground;
        private String iconsColor;
        private String paywallBackgroundColor;

        public CustomerPortalColorsPaletteInput(Map<String, Object> args) {
            if (args != null) {
                this.primary = (String) args.get("primary");
                this.textColor = (String) args.get("textColor");
                this.backgroundColor = (String) args.get("backgroundColor");
                this.borderColor = (String) args.get("borderColor");
                this.currentPlanBackground = (String) args.get("currentPlanBackground");
                this.iconsColor = (String) args.get("iconsColor");
                this.paywallBackgroundColor = (String) args.get("paywallBackgroundColor");
            }
        }

        public String getPrimary() { return this.primary; }
        public String getTextColor() { return this.textColor; }
        public String getBackgroundColor() { return this.backgroundColor; }
        public String getBorderColor() { return this.borderColor; }
        public String getCurrentPlanBackground() { return this.currentPlanBackground; }
        public String getIconsColor() { return this.iconsColor; }
        public String getPaywallBackgroundColor() { return this.paywallBackgroundColor; }
        public void setPrimary(String primary) { this.primary = primary; }
        public void setTextColor(String textColor) { this.textColor = textColor; }
        public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
        public void setBorderColor(String borderColor) { this.borderColor = borderColor; }
        public void setCurrentPlanBackground(String currentPlanBackground) { this.currentPlanBackground = currentPlanBackground; }
        public void setIconsColor(String iconsColor) { this.iconsColor = iconsColor; }
        public void setPaywallBackgroundColor(String paywallBackgroundColor) { this.paywallBackgroundColor = paywallBackgroundColor; }
    }
    public static class CheckoutConfigurationInput {
        private CheckoutPaletteInput palette;
        private TypographyConfigurationInput typography;
        private String customCss;
        private CheckoutContentInput content;

        public CheckoutConfigurationInput(Map<String, Object> args) {
            if (args != null) {
                this.palette = new CheckoutPaletteInput((Map<String, Object>) args.get("palette"));
                this.typography = new TypographyConfigurationInput((Map<String, Object>) args.get("typography"));
                this.customCss = (String) args.get("customCss");
                this.content = new CheckoutContentInput((Map<String, Object>) args.get("content"));
            }
        }

        public CheckoutPaletteInput getPalette() { return this.palette; }
        public TypographyConfigurationInput getTypography() { return this.typography; }
        public String getCustomCss() { return this.customCss; }
        public CheckoutContentInput getContent() { return this.content; }
        public void setPalette(CheckoutPaletteInput palette) { this.palette = palette; }
        public void setTypography(TypographyConfigurationInput typography) { this.typography = typography; }
        public void setCustomCss(String customCss) { this.customCss = customCss; }
        public void setContent(CheckoutContentInput content) { this.content = content; }
    }
    public static class CheckoutPaletteInput {
        private String primary;
        private String textColor;
        private String backgroundColor;
        private String borderColor;
        private String summaryBackgroundColor;

        public CheckoutPaletteInput(Map<String, Object> args) {
            if (args != null) {
                this.primary = (String) args.get("primary");
                this.textColor = (String) args.get("textColor");
                this.backgroundColor = (String) args.get("backgroundColor");
                this.borderColor = (String) args.get("borderColor");
                this.summaryBackgroundColor = (String) args.get("summaryBackgroundColor");
            }
        }

        public String getPrimary() { return this.primary; }
        public String getTextColor() { return this.textColor; }
        public String getBackgroundColor() { return this.backgroundColor; }
        public String getBorderColor() { return this.borderColor; }
        public String getSummaryBackgroundColor() { return this.summaryBackgroundColor; }
        public void setPrimary(String primary) { this.primary = primary; }
        public void setTextColor(String textColor) { this.textColor = textColor; }
        public void setBackgroundColor(String backgroundColor) { this.backgroundColor = backgroundColor; }
        public void setBorderColor(String borderColor) { this.borderColor = borderColor; }
        public void setSummaryBackgroundColor(String summaryBackgroundColor) { this.summaryBackgroundColor = summaryBackgroundColor; }
    }
    public static class CheckoutContentInput {
        private Boolean collectPhoneNumber;

        public CheckoutContentInput(Map<String, Object> args) {
            if (args != null) {
                this.collectPhoneNumber = (Boolean) args.get("collectPhoneNumber");
            }
        }

        public Boolean getCollectPhoneNumber() { return this.collectPhoneNumber; }
        public void setCollectPhoneNumber(Boolean collectPhoneNumber) { this.collectPhoneNumber = collectPhoneNumber; }
    }
    public static class SetCouponOnCustomerInput {
        private String id;
        private String relationId;

        public SetCouponOnCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class SetExperimentOnCustomerInput {
        private String id;
        private String relationId;

        public SetExperimentOnCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class RemoveCouponFromCustomerInput {
        private String id;
        private String relationId;

        public RemoveCouponFromCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class RemoveExperimentFromCustomerInput {
        private String id;
        private String relationId;

        public RemoveExperimentFromCustomerInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class PlanCreateInput {
        private String displayName;
        private String description;
        private String refId;
        private PackageStatus status;
        private String billingId;
        private String environmentId;
        private String productId;
        private Object additionalMetaData;
        private Iterable<WidgetType> hiddenFromWidgets;
        private String parentPlanId;

        public PlanCreateInput(Map<String, Object> args) {
            if (args != null) {
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                this.refId = (String) args.get("refId");
                if (args.get("status") instanceof PackageStatus) {
                    this.status = (PackageStatus) args.get("status");
                } else {
                    this.status = PackageStatus.valueOf((String) args.get("status"));
                }
                this.billingId = (String) args.get("billingId");
                this.environmentId = (String) args.get("environmentId");
                this.productId = (String) args.get("productId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
                this.parentPlanId = (String) args.get("parentPlanId");
            }
        }

        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public String getRefId() { return this.refId; }
        public PackageStatus getStatus() { return this.status; }
        public String getBillingId() { return this.billingId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getProductId() { return this.productId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public String getParentPlanId() { return this.parentPlanId; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setStatus(PackageStatus status) { this.status = status; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
        public void setParentPlanId(String parentPlanId) { this.parentPlanId = parentPlanId; }
    }
    public static class PackagePublishInput {
        private Object id;
        private PublishMigrationType migrationType;

        public PackagePublishInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (Object) args.get("id");
                if (args.get("migrationType") instanceof PublishMigrationType) {
                    this.migrationType = (PublishMigrationType) args.get("migrationType");
                } else {
                    this.migrationType = PublishMigrationType.valueOf((String) args.get("migrationType"));
                }
            }
        }

        public Object getId() { return this.id; }
        public PublishMigrationType getMigrationType() { return this.migrationType; }
        public void setId(Object id) { this.id = id; }
        public void setMigrationType(PublishMigrationType migrationType) { this.migrationType = migrationType; }
    }
    /** PublishMigrationType */
    public enum PublishMigrationType {
        NEW_CUSTOMERS,
        ALL_CUSTOMERS

    }

    public static class ArchivePlanInput {
        private String id;
        private String environmentId;

        public ArchivePlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getId() { return this.id; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setId(String id) { this.id = id; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class DiscardPackageDraftInput {
        private String refId;
        private String environmentId;

        public DiscardPackageDraftInput(Map<String, Object> args) {
            if (args != null) {
                this.refId = (String) args.get("refId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getRefId() { return this.refId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class PlanUpdateInput {
        private String id;
        private String displayName;
        private String description;
        private PackageStatus status;
        private String billingId;
        private String parentPlanId;
        private DefaultTrialConfigInputDtoInput defaultTrialConfig;
        private Object additionalMetaData;
        private Iterable<WidgetType> hiddenFromWidgets;

        public PlanUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                if (args.get("status") instanceof PackageStatus) {
                    this.status = (PackageStatus) args.get("status");
                } else {
                    this.status = PackageStatus.valueOf((String) args.get("status"));
                }
                this.billingId = (String) args.get("billingId");
                this.parentPlanId = (String) args.get("parentPlanId");
                this.defaultTrialConfig = new DefaultTrialConfigInputDtoInput((Map<String, Object>) args.get("defaultTrialConfig"));
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
            }
        }

        public String getId() { return this.id; }
        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public PackageStatus getStatus() { return this.status; }
        public String getBillingId() { return this.billingId; }
        public String getParentPlanId() { return this.parentPlanId; }
        public DefaultTrialConfigInputDtoInput getDefaultTrialConfig() { return this.defaultTrialConfig; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public void setId(String id) { this.id = id; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setStatus(PackageStatus status) { this.status = status; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setParentPlanId(String parentPlanId) { this.parentPlanId = parentPlanId; }
        public void setDefaultTrialConfig(DefaultTrialConfigInputDtoInput defaultTrialConfig) { this.defaultTrialConfig = defaultTrialConfig; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
    }
    public static class DefaultTrialConfigInputDtoInput {
        private TrialPeriodUnits units;
        private Double duration;

        public DefaultTrialConfigInputDtoInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("units") instanceof TrialPeriodUnits) {
                    this.units = (TrialPeriodUnits) args.get("units");
                } else {
                    this.units = TrialPeriodUnits.valueOf((String) args.get("units"));
                }
                this.duration = (Double) args.get("duration");
            }
        }

        public TrialPeriodUnits getUnits() { return this.units; }
        public Double getDuration() { return this.duration; }
        public void setUnits(TrialPeriodUnits units) { this.units = units; }
        public void setDuration(Double duration) { this.duration = duration; }
    }
    public static class AddonCreateInput {
        private String displayName;
        private String description;
        private String refId;
        private PackageStatus status;
        private String billingId;
        private String environmentId;
        private String productId;
        private Object additionalMetaData;
        private Iterable<WidgetType> hiddenFromWidgets;

        public AddonCreateInput(Map<String, Object> args) {
            if (args != null) {
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                this.refId = (String) args.get("refId");
                if (args.get("status") instanceof PackageStatus) {
                    this.status = (PackageStatus) args.get("status");
                } else {
                    this.status = PackageStatus.valueOf((String) args.get("status"));
                }
                this.billingId = (String) args.get("billingId");
                this.environmentId = (String) args.get("environmentId");
                this.productId = (String) args.get("productId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
            }
        }

        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public String getRefId() { return this.refId; }
        public PackageStatus getStatus() { return this.status; }
        public String getBillingId() { return this.billingId; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getProductId() { return this.productId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setRefId(String refId) { this.refId = refId; }
        public void setStatus(PackageStatus status) { this.status = status; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setProductId(String productId) { this.productId = productId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
    }
    public static class AddonUpdateInput {
        private String id;
        private String displayName;
        private String description;
        private PackageStatus status;
        private String billingId;
        private Object additionalMetaData;
        private Iterable<WidgetType> hiddenFromWidgets;

        public AddonUpdateInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.displayName = (String) args.get("displayName");
                this.description = (String) args.get("description");
                if (args.get("status") instanceof PackageStatus) {
                    this.status = (PackageStatus) args.get("status");
                } else {
                    this.status = PackageStatus.valueOf((String) args.get("status"));
                }
                this.billingId = (String) args.get("billingId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                if (args.get("hiddenFromWidgets") != null) {
                    this.hiddenFromWidgets = (Iterable<WidgetType>) args.get("hiddenFromWidgets");
                }
            }
        }

        public String getId() { return this.id; }
        public String getDisplayName() { return this.displayName; }
        public String getDescription() { return this.description; }
        public PackageStatus getStatus() { return this.status; }
        public String getBillingId() { return this.billingId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public Iterable<WidgetType> getHiddenFromWidgets() { return this.hiddenFromWidgets; }
        public void setId(String id) { this.id = id; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public void setDescription(String description) { this.description = description; }
        public void setStatus(PackageStatus status) { this.status = status; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setHiddenFromWidgets(Iterable<WidgetType> hiddenFromWidgets) { this.hiddenFromWidgets = hiddenFromWidgets; }
    }
    public static class PackagePricingInput {
        private PricingType pricingType;
        private String environmentId;
        private String packageId;
        private PricingModelCreateInput pricingModel;
        private Iterable<PricingModelCreateInput> pricingModels;

        public PackagePricingInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("pricingType") instanceof PricingType) {
                    this.pricingType = (PricingType) args.get("pricingType");
                } else {
                    this.pricingType = PricingType.valueOf((String) args.get("pricingType"));
                }
                this.environmentId = (String) args.get("environmentId");
                this.packageId = (String) args.get("packageId");
                this.pricingModel = new PricingModelCreateInput((Map<String, Object>) args.get("pricingModel"));
                if (args.get("pricingModels") != null) {
                    this.pricingModels = (Iterable<PricingModelCreateInput>) args.get("pricingModels");
                }
            }
        }

        public PricingType getPricingType() { return this.pricingType; }
        public String getEnvironmentId() { return this.environmentId; }
        public String getPackageId() { return this.packageId; }
        public PricingModelCreateInput getPricingModel() { return this.pricingModel; }
        public Iterable<PricingModelCreateInput> getPricingModels() { return this.pricingModels; }
        public void setPricingType(PricingType pricingType) { this.pricingType = pricingType; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setPackageId(String packageId) { this.packageId = packageId; }
        public void setPricingModel(PricingModelCreateInput pricingModel) { this.pricingModel = pricingModel; }
        public void setPricingModels(Iterable<PricingModelCreateInput> pricingModels) { this.pricingModels = pricingModels; }
    }
    public static class PricingModelCreateInput {
        private BillingModel billingModel;
        private TiersMode tiersMode;
        private Iterable<PricePeriodInput> pricePeriods;
        private String featureId;
        private Double minUnitQuantity;
        private Double maxUnitQuantity;

        public PricingModelCreateInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("billingModel") instanceof BillingModel) {
                    this.billingModel = (BillingModel) args.get("billingModel");
                } else {
                    this.billingModel = BillingModel.valueOf((String) args.get("billingModel"));
                }
                if (args.get("tiersMode") instanceof TiersMode) {
                    this.tiersMode = (TiersMode) args.get("tiersMode");
                } else {
                    this.tiersMode = TiersMode.valueOf((String) args.get("tiersMode"));
                }
                if (args.get("pricePeriods") != null) {
                    this.pricePeriods = (Iterable<PricePeriodInput>) args.get("pricePeriods");
                }
                this.featureId = (String) args.get("featureId");
                this.minUnitQuantity = (Double) args.get("minUnitQuantity");
                this.maxUnitQuantity = (Double) args.get("maxUnitQuantity");
            }
        }

        public BillingModel getBillingModel() { return this.billingModel; }
        public TiersMode getTiersMode() { return this.tiersMode; }
        public Iterable<PricePeriodInput> getPricePeriods() { return this.pricePeriods; }
        public String getFeatureId() { return this.featureId; }
        public Double getMinUnitQuantity() { return this.minUnitQuantity; }
        public Double getMaxUnitQuantity() { return this.maxUnitQuantity; }
        public void setBillingModel(BillingModel billingModel) { this.billingModel = billingModel; }
        public void setTiersMode(TiersMode tiersMode) { this.tiersMode = tiersMode; }
        public void setPricePeriods(Iterable<PricePeriodInput> pricePeriods) { this.pricePeriods = pricePeriods; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setMinUnitQuantity(Double minUnitQuantity) { this.minUnitQuantity = minUnitQuantity; }
        public void setMaxUnitQuantity(Double maxUnitQuantity) { this.maxUnitQuantity = maxUnitQuantity; }
    }
    public static class PricePeriodInput {
        private BillingPeriod billingPeriod;
        private String billingCountryCode;
        private MoneyInputDtoInput price;
        private Iterable<PriceTierInput> tiers;

        public PricePeriodInput(Map<String, Object> args) {
            if (args != null) {
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.billingCountryCode = (String) args.get("billingCountryCode");
                this.price = new MoneyInputDtoInput((Map<String, Object>) args.get("price"));
                if (args.get("tiers") != null) {
                    this.tiers = (Iterable<PriceTierInput>) args.get("tiers");
                }
            }
        }

        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public MoneyInputDtoInput getPrice() { return this.price; }
        public Iterable<PriceTierInput> getTiers() { return this.tiers; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setPrice(MoneyInputDtoInput price) { this.price = price; }
        public void setTiers(Iterable<PriceTierInput> tiers) { this.tiers = tiers; }
    }
    public static class MoneyInputDtoInput {
        private Double amount;
        private Currency currency;

        public MoneyInputDtoInput(Map<String, Object> args) {
            if (args != null) {
                this.amount = (Double) args.get("amount");
                if (args.get("currency") instanceof Currency) {
                    this.currency = (Currency) args.get("currency");
                } else {
                    this.currency = Currency.valueOf((String) args.get("currency"));
                }
            }
        }

        public Double getAmount() { return this.amount; }
        public Currency getCurrency() { return this.currency; }
        public void setAmount(Double amount) { this.amount = amount; }
        public void setCurrency(Currency currency) { this.currency = currency; }
    }
    public static class PriceTierInput {
        private Double upTo;
        private MoneyInputDtoInput unitPrice;

        public PriceTierInput(Map<String, Object> args) {
            if (args != null) {
                this.upTo = (Double) args.get("upTo");
                this.unitPrice = new MoneyInputDtoInput((Map<String, Object>) args.get("unitPrice"));
            }
        }

        public Double getUpTo() { return this.upTo; }
        public MoneyInputDtoInput getUnitPrice() { return this.unitPrice; }
        public void setUpTo(Double upTo) { this.upTo = upTo; }
        public void setUnitPrice(MoneyInputDtoInput unitPrice) { this.unitPrice = unitPrice; }
    }
    public static class SetBasePlanOnPlanInput {
        private String id;
        private String relationId;

        public SetBasePlanOnPlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class AddCompatibleAddonsToPlanInput {
        private String id;
        private Iterable<String> relationIds;

        public AddCompatibleAddonsToPlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationIds = (Iterable<String>) args.get("relationIds");
            }
        }

        public String getId() { return this.id; }
        public Iterable<String> getRelationIds() { return this.relationIds; }
        public void setId(String id) { this.id = id; }
        public void setRelationIds(Iterable<String> relationIds) { this.relationIds = relationIds; }
    }
    public static class SetCompatibleAddonsOnPlanInput {
        private String id;
        private Iterable<String> relationIds;

        public SetCompatibleAddonsOnPlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationIds = (Iterable<String>) args.get("relationIds");
            }
        }

        public String getId() { return this.id; }
        public Iterable<String> getRelationIds() { return this.relationIds; }
        public void setId(String id) { this.id = id; }
        public void setRelationIds(Iterable<String> relationIds) { this.relationIds = relationIds; }
    }
    public static class RemoveBasePlanFromPlanInput {
        private String id;
        private String relationId;

        public RemoveBasePlanFromPlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationId = (String) args.get("relationId");
            }
        }

        public String getId() { return this.id; }
        public String getRelationId() { return this.relationId; }
        public void setId(String id) { this.id = id; }
        public void setRelationId(String relationId) { this.relationId = relationId; }
    }
    public static class RemoveCompatibleAddonsFromPlanInput {
        private String id;
        private Iterable<String> relationIds;

        public RemoveCompatibleAddonsFromPlanInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.relationIds = (Iterable<String>) args.get("relationIds");
            }
        }

        public String getId() { return this.id; }
        public Iterable<String> getRelationIds() { return this.relationIds; }
        public void setId(String id) { this.id = id; }
        public void setRelationIds(Iterable<String> relationIds) { this.relationIds = relationIds; }
    }
    public static class DeleteOneAddonInput {
        private String id;

        public DeleteOneAddonInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class DeleteOnePriceInput {
        private String id;

        public DeleteOnePriceInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class UsageMeasurementCreateInput {
        private Double value;
        private String featureId;
        private String customerId;
        private String resourceId;
        private Object createdAt;
        private UsageUpdateBehavior updateBehavior;
        private String environmentId;

        public UsageMeasurementCreateInput(Map<String, Object> args) {
            if (args != null) {
                this.value = (Double) args.get("value");
                this.featureId = (String) args.get("featureId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.createdAt = (Object) args.get("createdAt");
                if (args.get("updateBehavior") instanceof UsageUpdateBehavior) {
                    this.updateBehavior = (UsageUpdateBehavior) args.get("updateBehavior");
                } else {
                    this.updateBehavior = UsageUpdateBehavior.valueOf((String) args.get("updateBehavior"));
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Double getValue() { return this.value; }
        public String getFeatureId() { return this.featureId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public Object getCreatedAt() { return this.createdAt; }
        public UsageUpdateBehavior getUpdateBehavior() { return this.updateBehavior; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setValue(Double value) { this.value = value; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setUpdateBehavior(UsageUpdateBehavior updateBehavior) { this.updateBehavior = updateBehavior; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public enum UsageUpdateBehavior {
        DELTA,
        SET

    }

    public static class ReportUsageInput {
        private Double value;
        private String featureId;
        private String customerId;
        private String resourceId;
        private Object createdAt;
        private UsageUpdateBehavior updateBehavior;
        private String environmentId;

        public ReportUsageInput(Map<String, Object> args) {
            if (args != null) {
                this.value = (Double) args.get("value");
                this.featureId = (String) args.get("featureId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.createdAt = (Object) args.get("createdAt");
                if (args.get("updateBehavior") instanceof UsageUpdateBehavior) {
                    this.updateBehavior = (UsageUpdateBehavior) args.get("updateBehavior");
                } else {
                    this.updateBehavior = UsageUpdateBehavior.valueOf((String) args.get("updateBehavior"));
                }
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public Double getValue() { return this.value; }
        public String getFeatureId() { return this.featureId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public Object getCreatedAt() { return this.createdAt; }
        public UsageUpdateBehavior getUpdateBehavior() { return this.updateBehavior; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setValue(Double value) { this.value = value; }
        public void setFeatureId(String featureId) { this.featureId = featureId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setUpdateBehavior(UsageUpdateBehavior updateBehavior) { this.updateBehavior = updateBehavior; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class UsageEventsReportInput {
        private String environmentId;
        private Iterable<UsageEventReportInput> usageEvents;

        public UsageEventsReportInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                if (args.get("usageEvents") != null) {
                    this.usageEvents = (Iterable<UsageEventReportInput>) args.get("usageEvents");
                }
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public Iterable<UsageEventReportInput> getUsageEvents() { return this.usageEvents; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setUsageEvents(Iterable<UsageEventReportInput> usageEvents) { this.usageEvents = usageEvents; }
    }
    public static class UsageEventReportInput {
        private String eventName;
        private String customerId;
        private String resourceId;
        private Object timestamp;
        private String idempotencyKey;
        private Object dimensions;

        public UsageEventReportInput(Map<String, Object> args) {
            if (args != null) {
                this.eventName = (String) args.get("eventName");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.timestamp = (Object) args.get("timestamp");
                this.idempotencyKey = (String) args.get("idempotencyKey");
                this.dimensions = (Object) args.get("dimensions");
            }
        }

        public String getEventName() { return this.eventName; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public Object getTimestamp() { return this.timestamp; }
        public String getIdempotencyKey() { return this.idempotencyKey; }
        public Object getDimensions() { return this.dimensions; }
        public void setEventName(String eventName) { this.eventName = eventName; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setTimestamp(Object timestamp) { this.timestamp = timestamp; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
        public void setDimensions(Object dimensions) { this.dimensions = dimensions; }
    }
    public static class CreateOneHookInput {
        private CreateHookInput hook;

        public CreateOneHookInput(Map<String, Object> args) {
            if (args != null) {
                this.hook = new CreateHookInput((Map<String, Object>) args.get("hook"));
            }
        }

        public CreateHookInput getHook() { return this.hook; }
        public void setHook(CreateHookInput hook) { this.hook = hook; }
    }
    public static class CreateHookInput {
        private String id;
        private String description;
        private String secretKey;
        private String endpoint;
        private HookStatus status;
        private Object createdAt;
        private String environmentId;
        private Iterable<EventLogType> eventLogTypes;

        public CreateHookInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.description = (String) args.get("description");
                this.secretKey = (String) args.get("secretKey");
                this.endpoint = (String) args.get("endpoint");
                if (args.get("status") instanceof HookStatus) {
                    this.status = (HookStatus) args.get("status");
                } else {
                    this.status = HookStatus.valueOf((String) args.get("status"));
                }
                this.createdAt = (Object) args.get("createdAt");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("eventLogTypes") != null) {
                    this.eventLogTypes = (Iterable<EventLogType>) args.get("eventLogTypes");
                }
            }
        }

        public String getId() { return this.id; }
        public String getDescription() { return this.description; }
        public String getSecretKey() { return this.secretKey; }
        public String getEndpoint() { return this.endpoint; }
        public HookStatus getStatus() { return this.status; }
        public Object getCreatedAt() { return this.createdAt; }
        public String getEnvironmentId() { return this.environmentId; }
        public Iterable<EventLogType> getEventLogTypes() { return this.eventLogTypes; }
        public void setId(String id) { this.id = id; }
        public void setDescription(String description) { this.description = description; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public void setStatus(HookStatus status) { this.status = status; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setEventLogTypes(Iterable<EventLogType> eventLogTypes) { this.eventLogTypes = eventLogTypes; }
    }
    public static class UpdateOneHookInput {
        private String id;
        private UpdateHookInput update;

        public UpdateOneHookInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.update = new UpdateHookInput((Map<String, Object>) args.get("update"));
            }
        }

        public String getId() { return this.id; }
        public UpdateHookInput getUpdate() { return this.update; }
        public void setId(String id) { this.id = id; }
        public void setUpdate(UpdateHookInput update) { this.update = update; }
    }
    public static class UpdateHookInput {
        private String id;
        private String description;
        private String secretKey;
        private String endpoint;
        private HookStatus status;
        private Object createdAt;
        private String environmentId;
        private Iterable<EventLogType> eventLogTypes;

        public UpdateHookInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
                this.description = (String) args.get("description");
                this.secretKey = (String) args.get("secretKey");
                this.endpoint = (String) args.get("endpoint");
                if (args.get("status") instanceof HookStatus) {
                    this.status = (HookStatus) args.get("status");
                } else {
                    this.status = HookStatus.valueOf((String) args.get("status"));
                }
                this.createdAt = (Object) args.get("createdAt");
                this.environmentId = (String) args.get("environmentId");
                if (args.get("eventLogTypes") != null) {
                    this.eventLogTypes = (Iterable<EventLogType>) args.get("eventLogTypes");
                }
            }
        }

        public String getId() { return this.id; }
        public String getDescription() { return this.description; }
        public String getSecretKey() { return this.secretKey; }
        public String getEndpoint() { return this.endpoint; }
        public HookStatus getStatus() { return this.status; }
        public Object getCreatedAt() { return this.createdAt; }
        public String getEnvironmentId() { return this.environmentId; }
        public Iterable<EventLogType> getEventLogTypes() { return this.eventLogTypes; }
        public void setId(String id) { this.id = id; }
        public void setDescription(String description) { this.description = description; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public void setStatus(HookStatus status) { this.status = status; }
        public void setCreatedAt(Object createdAt) { this.createdAt = createdAt; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setEventLogTypes(Iterable<EventLogType> eventLogTypes) { this.eventLogTypes = eventLogTypes; }
    }
    public static class DeleteOneHookInput {
        private String id;

        public DeleteOneHookInput(Map<String, Object> args) {
            if (args != null) {
                this.id = (String) args.get("id");
            }
        }

        public String getId() { return this.id; }
        public void setId(String id) { this.id = id; }
    }
    public static class ClearCustomerPersistentCacheInput {
        private String customerId;
        private String resourceId;
        private String environmentId;

        public ClearCustomerPersistentCacheInput(Map<String, Object> args) {
            if (args != null) {
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getEnvironmentId() { return this.environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }
    public static class ApplySubscriptionInput {
        private String planId;
        private BillingPeriod billingPeriod;
        private Double unitQuantity;
        private Iterable<SubscriptionAddonInput> addons;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Object startDate;
        private String resourceId;
        private Object additionalMetaData;
        private SubscriptionBillingInfoInput billingInformation;
        private String billingId;
        private String promotionCode;
        private String billingCountryCode;
        private Iterable<SubscriptionEntitlementInput> subscriptionEntitlements;
        private String customerId;
        private Boolean skipTrial;
        private String paymentMethodId;

        public ApplySubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                this.startDate = (Object) args.get("startDate");
                this.resourceId = (String) args.get("resourceId");
                this.additionalMetaData = (Object) args.get("additionalMetaData");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.billingId = (String) args.get("billingId");
                this.promotionCode = (String) args.get("promotionCode");
                this.billingCountryCode = (String) args.get("billingCountryCode");
                if (args.get("subscriptionEntitlements") != null) {
                    this.subscriptionEntitlements = (Iterable<SubscriptionEntitlementInput>) args.get("subscriptionEntitlements");
                }
                this.customerId = (String) args.get("customerId");
                this.skipTrial = (Boolean) args.get("skipTrial");
                this.paymentMethodId = (String) args.get("paymentMethodId");
            }
        }

        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Object getStartDate() { return this.startDate; }
        public String getResourceId() { return this.resourceId; }
        public Object getAdditionalMetaData() { return this.additionalMetaData; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getBillingId() { return this.billingId; }
        public String getPromotionCode() { return this.promotionCode; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Iterable<SubscriptionEntitlementInput> getSubscriptionEntitlements() { return this.subscriptionEntitlements; }
        public String getCustomerId() { return this.customerId; }
        public Boolean getSkipTrial() { return this.skipTrial; }
        public String getPaymentMethodId() { return this.paymentMethodId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setAdditionalMetaData(Object additionalMetaData) { this.additionalMetaData = additionalMetaData; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setBillingId(String billingId) { this.billingId = billingId; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setSubscriptionEntitlements(Iterable<SubscriptionEntitlementInput> subscriptionEntitlements) { this.subscriptionEntitlements = subscriptionEntitlements; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setSkipTrial(Boolean skipTrial) { this.skipTrial = skipTrial; }
        public void setPaymentMethodId(String paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    }
    public static class PreviewSubscriptionInput {
        private String environmentId;
        private String customerId;
        private String resourceId;
        private String planId;
        private BillingPeriod billingPeriod;
        private String billingCountryCode;
        private Double unitQuantity;
        private Iterable<BillableFeatureInput> billableFeatures;
        private Iterable<SubscriptionAddonInput> addons;
        private Object startDate;
        private SubscriptionBillingInfoInput billingInformation;
        private String promotionCode;

        public PreviewSubscriptionInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
                this.customerId = (String) args.get("customerId");
                this.resourceId = (String) args.get("resourceId");
                this.planId = (String) args.get("planId");
                if (args.get("billingPeriod") instanceof BillingPeriod) {
                    this.billingPeriod = (BillingPeriod) args.get("billingPeriod");
                } else {
                    this.billingPeriod = BillingPeriod.valueOf((String) args.get("billingPeriod"));
                }
                this.billingCountryCode = (String) args.get("billingCountryCode");
                this.unitQuantity = (Double) args.get("unitQuantity");
                if (args.get("billableFeatures") != null) {
                    this.billableFeatures = (Iterable<BillableFeatureInput>) args.get("billableFeatures");
                }
                if (args.get("addons") != null) {
                    this.addons = (Iterable<SubscriptionAddonInput>) args.get("addons");
                }
                this.startDate = (Object) args.get("startDate");
                this.billingInformation = new SubscriptionBillingInfoInput((Map<String, Object>) args.get("billingInformation"));
                this.promotionCode = (String) args.get("promotionCode");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public String getCustomerId() { return this.customerId; }
        public String getResourceId() { return this.resourceId; }
        public String getPlanId() { return this.planId; }
        public BillingPeriod getBillingPeriod() { return this.billingPeriod; }
        public String getBillingCountryCode() { return this.billingCountryCode; }
        public Double getUnitQuantity() { return this.unitQuantity; }
        public Iterable<BillableFeatureInput> getBillableFeatures() { return this.billableFeatures; }
        public Iterable<SubscriptionAddonInput> getAddons() { return this.addons; }
        public Object getStartDate() { return this.startDate; }
        public SubscriptionBillingInfoInput getBillingInformation() { return this.billingInformation; }
        public String getPromotionCode() { return this.promotionCode; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public void setPlanId(String planId) { this.planId = planId; }
        public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
        public void setBillingCountryCode(String billingCountryCode) { this.billingCountryCode = billingCountryCode; }
        public void setUnitQuantity(Double unitQuantity) { this.unitQuantity = unitQuantity; }
        public void setBillableFeatures(Iterable<BillableFeatureInput> billableFeatures) { this.billableFeatures = billableFeatures; }
        public void setAddons(Iterable<SubscriptionAddonInput> addons) { this.addons = addons; }
        public void setStartDate(Object startDate) { this.startDate = startDate; }
        public void setBillingInformation(SubscriptionBillingInfoInput billingInformation) { this.billingInformation = billingInformation; }
        public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
    }
    public static class SyncTaxRatesInput {
        private String environmentId;

        public SyncTaxRatesInput(Map<String, Object> args) {
            if (args != null) {
                this.environmentId = (String) args.get("environmentId");
            }
        }

        public String getEnvironmentId() { return this.environmentId; }
        public void setEnvironmentId(String environmentId) { this.environmentId = environmentId; }
    }

}
