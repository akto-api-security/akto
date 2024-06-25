package com.akto.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.akto.dao.AccountsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.mongodb.client.model.Filters;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountTask {
    private static final Logger logger = LoggerFactory.getLogger(AccountTask.class);
    public static final AccountTask instance = new AccountTask();

    public static final Set<Integer> inactiveAccountsSet = new HashSet(Arrays.asList(
            1698166879, 1701109976, 1690222721, 1700421987, 1692653749, 1698114809, 1692928130, 1690570731, 1695581777,1692193671,
            1700455610, 1689888960, 1696391279, 1693288165, 1698331284, 1698116613, 1695160251, 1699511169, 1698201149, 1691024024,
            1697579719, 1690780188, 1699345772, 1691467974, 1692221066, 1697099439, 1690004941, 1698928692, 1699938165,
            1691113168, 1700178127, 1699199732, 1698254435, 1696064529, 1700189423, 1695337133, 1699626391, 1697233152,
            1689890267, 1691369475, 1690022646, 1690937231, 1690326067, 1693510631, 1698414451, 1695778515, 1689970903,
            1691542898, 1695691204, 1690260379, 1694918846, 1690759941, 1696924647, 1695451102, 1690389597, 1698978994,
            1694366996, 1697576608, 1690250942, 1701120240, 1692245328, 1700306097, 1691916150, 1693698961, 1698469475,
            1689998039, 1693929926, 1697401113, 1701018082, 1695494032, 1696865979, 1690090506, 1692656847, 1695149808,
            1700062187, 1699487208, 1692835078, 1690768986, 1691734935, 1697169443, 1701033605, 1691202646, 1694397153,
            1693998783, 1700262205, 1699219427, 1691675338, 1697760716, 1696481604, 1699694369, 1697569966, 1695094242,
            1698766098, 1692648365, 1695270589, 1693506187, 1692887031, 1699523573, 1693975788, 1701145921, 1700571811,
            1700492038, 1698161348, 1700787326, 1689820721, 1696568503, 1692194213, 1694725167, 1696227309, 1692533146,
            1692887537, 1701137003, 1690078846, 1695608028, 1692055862, 1699490590, 1690181532, 1693215848, 1699380136,
            1690244397, 1694050686, 1697331690, 1691891526, 1692310924, 1690235207, 1701117446, 1698646837, 1695124344,
            1692421277, 1691356538, 1694442409, 1694611074, 1692195914, 1689908367, 1690934524, 1696309975, 1697432351,
            1700191003, 1690003811, 1697094377, 1700442129, 1692193649, 1700607968, 1697225026, 1692825826, 1693652719,
            1694653991, 1698835097, 1690021956, 1691949404, 1697946609, 1698330862, 1698967194, 1691014255, 1690504753,
            1692735935, 1699392411, 1699560746, 1692223381, 1697061341, 1690109728, 1695949566, 1699906129, 1698414139,
            1698610923, 1694530343, 1696821345, 1693503854, 1697744966, 1697583332, 1700472954, 1698798273, 1696979677,
            1700657005, 1695447097, 1692951204, 1695715381, 1699381957, 1698331255, 1689982750, 1693528155, 1699892207,
            1700433681, 1692130764, 1692683631, 1690746260, 1693484929, 1699386823, 1698124715, 1698331459, 1692193626,
            1698906135, 1695877265, 1699059034, 1699314362, 1692536237, 1694681418, 1701327172, 1698161367, 1700112256,
            1695666505, 1693648859, 1699946463, 1700004714, 1696965431, 1695934297, 1697012902, 1697747408, 1689818518,
            1692223439, 1690472694, 1691701648, 1692194183, 1699157996, 1697355890, 1694469694, 1700551986, 1695128492,
            1692887084, 1697389184, 1692534245, 1698331187, 1690919802, 1694380695, 1697594460, 1689954567, 1690289103,
            1693074476, 1692397395, 1691218715, 1691457785, 1692242109, 1693632503, 1697210341, 1694878769, 1696178518,
            1694417493, 1700689356, 1691434785, 1692835567, 1691144731, 1692879591, 1692335583, 1690087083, 1699793900,
            1693442240, 1692161290, 1692156357, 1692887726, 1698779698, 1701004993, 1700597392, 1696873301, 1690162182,
            1691009785, 1691094369, 1691310943, 1699211128, 1698414107, 1692175521, 1700227752, 1700376941, 1690994657,
            1692009663, 1693271598, 1698161358, 1695098804, 1693878286, 1692207649, 1697781302, 1698724344, 1698907673,
            1698583105, 1700709234, 1694420592, 1691311684, 1700031937, 1692749217, 1692514447, 1690942162, 1697062698,
            1697393552, 1700422310, 1692991970, 1699376768, 1692140846, 1696448713, 1700727504, 1689901103, 1694047973,
            1693799329, 1699074032, 1698471835, 1701188409, 1690320704, 1692193706, 1697731879, 1698203367, 1690853672,
            1698098028, 1692193855, 1692194231, 1694215131, 1697702794, 1700430313, 1698614571, 1696801684, 1697257441,
            1694990485, 1693828325, 1692193811, 1692887302, 1698950996, 1692136053, 1699925779, 1692065021, 1698393685,
            1696303949, 1701177559, 1695996945, 1698075008, 1700716457, 1694992054, 1689870569, 1695242256, 1691982358,
            1691365318, 1696881156, 1693529339, 1696172131, 1697012692, 1699028893, 1701126613, 1692414456, 1695334457,
            1700760268, 1690849366, 1696362737, 1691406413, 1695955884, 1696987378, 1695664511, 1690954535, 1690160897,
            1690133953, 1691626149, 1699434784, 1695268264, 1689824803, 1698548585, 1689967651, 1693298701, 1701064712,
            1701126302, 1693939881, 1695356176, 1692214863, 1694939027, 1697668011, 1694102773, 1696223646, 1697275304,
            1696097888, 1694721402, 1698732777, 1699187870, 1690308666, 1692605844, 1696384160, 1696195770, 1699805656,
            1693926459, 1695609202, 1700621698, 1701041213, 1690992708, 1694566217, 1695192689, 1698331298, 1698348803,
            1700757157, 1696863833, 1696394389, 1697495520, 1695073684, 1691566085, 1696740306, 1692721882, 1693449506,
            1699400655, 1693927626, 1690481662, 1693936923, 1693864209, 1692431814, 1698414658, 1700761005, 1691185248,
            1699377706, 1693965351, 1697855375, 1699859978, 1690192747, 1690941946, 1693751740, 1690204012, 1691825122,
            1695349345, 1690918044, 1697861221, 1698263234, 1696625410, 1697669752, 1698634233, 1698331199, 1692939110,
            1693340718, 1691001256, 1697746135, 1690423553, 1692906485, 1698233151, 1697781565, 1696743785, 1690269559,
            1691369873, 1693413622, 1691592547, 1699035234, 1693894859, 1697438007, 1702426130, 1692578924, 1690315568,
            1693247518, 1697136896, 1691543590, 1694849162, 1697576515, 1696904849, 1697694713, 1693082763, 1698022131,
            1699299386, 1692193782, 1698121895, 1698331190, 1698777185, 1693320774, 1691368028, 1697211102, 1690335235,
            1698414223, 1699651128, 1696819801, 1695671499, 1698473448, 1692193679, 1695613281, 1689881906, 1690076003,
            1698465619, 1690382602, 1692752060, 1695412803, 1696573279, 1693168180, 1690293573, 1700121054, 1694648033,
            1690898567, 1696246838, 1690545062, 1690903790, 1692903716, 1699972132, 1691199544, 1699079326, 1696126615,
            1692833810, 1691150888, 1692052029, 1696046375, 1691540229, 1698622537, 1697059307, 1692887191, 1699526680,
            1693767926, 1700162013, 1694724088, 1693299824, 1694928463, 1699798258, 1692573351, 1695611606, 1690430301,
            1693421754, 1692888064, 1698914917, 1698386116, 1689799686, 1697081727, 1690540341, 1693714714, 1690874270,
            1695885340, 1700022623, 1691427402, 1698021596, 1692195368, 1694897365, 1691633721, 1692859094, 1695857314,
            1691283933, 1698437696, 1689985308, 1690613057, 1698265595, 1700787018, 1695437787, 1699426186, 1695012312,
            1692893099, 1692682709, 1698415232, 1695977549, 1695591240, 1699007019, 1691136117, 1697695877, 1693413862,
            1697146340, 1698775756, 1698331575, 1694579246, 1696996897, 1700715361, 1697429556, 1695235093, 1697179027,
            1699842015, 1690957577, 1692193825, 1700070659, 1695719884, 1698166367, 1691620105, 1692646346, 1694642947,
            1692724759, 1690721411, 1692507122, 1696214245, 1697560082, 1690491860, 1698038219, 1693203820, 1699479733,
            1690941361, 1700076786, 1698331200, 1701042327, 1690257207, 1696489601, 1696287251, 1690885161, 1694570759,
            1689984572, 1697765244, 1694723643, 1693247915, 1695661992, 1695784040, 1700313461, 1700085340, 1698346241,
            1698710974, 1690334765, 1694761720, 1696380941, 1694207434, 1697163447, 1694436471, 1691957233, 1693246919,
            1689940448, 1697648225, 1693254230, 1700111915, 1692912626, 1693166929, 1697085096, 1698475207, 1700449227,
            1691510592, 1695184753, 1696787011, 1699908233, 1698177398, 1699904244, 1694229649, 1691557372, 1689895634,
            1697377451, 1691460675, 1695714552, 1692828979, 1698629321, 1693836979, 1698138651, 1698331576, 1698791354,
            1689824182, 1690489999, 1700573377, 1695106477, 1691803911, 1694050128, 1697078255, 1690488081, 1694149865,
            1693920007, 1699727389, 1690617075, 1693512739, 1697227157, 1698199747, 1700996055, 1692193521, 1698291850,
            1696798844, 1690512711, 1690971838, 1697088224, 1697706615, 1693168678, 1699812443, 1700437102, 1700080403,
            1698110998, 1699163359, 1693848193, 1701081803, 1698311980, 1697571544, 1695609208, 1699132448, 1692895563,
            1692773620, 1692552231, 1693669229, 1698133616, 1691708849, 1699967709, 1700161330, 1692193657, 1701021538,
            1691202831, 1695141251, 1691821983, 1691984595, 1690940911, 1697136623, 1696820352, 1689823566, 1692887072,
            1690672691, 1696948962, 1700601626, 1698546368, 1691683113, 1694620663, 1692888335, 1694986616, 1692491984,
            1699930961, 1698414095, 1699769511, 1694969528, 1693780359, 1694478778, 1698203170, 1698091924, 1692194075,
            1692622918, 1698609513, 1694374585, 1695711777, 1694161819, 1699829004, 1698331471, 1690191326, 1693021017,
            1698376233, 1690848264, 1699828511, 1696481511, 1700116956, 1699686528, 1693332485, 1695901847, 1692194911,
            1691592224, 1697123813, 1692752370, 1696983019, 1691306873, 1695452963, 1691345392, 1692852046, 1698185644,
            1697298753, 1690348834, 1694744061, 1692194089, 1700978295, 1692887047, 1694479260, 1694895391, 1697048134,
            1692850178, 1696963328, 1697490754, 1698415778, 1694592644, 1692193639, 1698663332, 1699011144, 1692705938,
            1692490152, 1692249490, 1693580236, 1697360398, 1692656462, 1696898538, 1692320480, 1699323666, 1694134275,
            1692934339, 1694617233, 1695663354, 1689846663, 1700707790, 1700964512, 1692886939, 1695089424, 1692937905,
            1696731192, 1695182048, 1692350231, 1692886956, 1690399864, 1692196239, 1692658409, 1697438807, 1690936946,
            1692714691, 1690941701, 1692211903, 1692195407, 1697123660, 1699949805, 1691633381, 1691218976, 1694046433,
            1699944154, 1689985826, 1691981081, 1692817771, 1689820992, 1690852086, 1697938109, 1692631224, 1690889926,
            1698494155, 1692237041, 1698191420, 1699825878, 1692040951, 1695965048, 1698792873, 1693847454, 1698651233,
            1692193934, 1700437044, 1700979890, 1697768883, 1691339865, 1695268012, 1692205196, 1699494362, 1699555765,
            1694404381, 1691110878, 1694742924, 1692047956, 1697247018, 1698197541, 1699482960, 1696888352, 1698016044,
            1699241134, 1692888253, 1693594787, 1698264867, 1699158086, 1697147431, 1694835484, 1694759602, 1694808742,
            1692194630, 1695253515, 1699148653, 1690892023, 1700591679, 1696321249, 1699994696, 1697672499, 1697590453,
            1700247456, 1691692570, 1693241353, 1691435648, 1700863458));



    public void executeTask(Consumer<Account> consumeAccount, String taskName) {
        try {
            Bson activeFilter = Filters.or(
                    Filters.exists(Account.INACTIVE_STR, false),
                    Filters.eq(Account.INACTIVE_STR, false));

            List<Account> activeAccounts = AccountsDao.instance.findAll(activeFilter);
            if (activeAccounts == null || activeAccounts.isEmpty()) {
                logger.error("Active accounts is null or empty");
                return;
            }
            logger.info("Active accounts found: " + activeAccounts.size());
            for (Account account : activeAccounts) {
                if (inactiveAccountsSet.contains(account.getId())) {
                    continue;
                }
                try {
                    Context.accountId.set(account.getId());
                    consumeAccount.accept(account);
                } catch (Exception e) {
                    String msgString = String.format("Error in executing task %s for account %d", taskName,
                            account.getId());
                    logger.error(msgString, e);
                }
            }
        } catch (Exception e) {
            String err = "";
            if (e != null && e.getStackTrace() != null && e.getStackTrace().length > 0) {
                StackTraceElement stackTraceElement = e.getStackTrace()[0];
                err = String.format("Err msg: %s\nClass: %s\nFile: %s\nLine: %d", err, stackTraceElement.getClassName(), stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
            } else {
                err = String.format("Err msg: %s\nStackTrace not available", err);
                e.printStackTrace();
            }
            logger.error("Error in execute task: " + err);
        }

    }

    public void executeTaskHybridAccounts(Consumer<Account> consumeAccount, String taskName) {

        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        Bson accountFilter = Filters.eq(Account.HYBRID_SAAS_ACCOUNT, true);

        Bson combinedFilter = Filters.and(activeFilter, accountFilter);

        List<Account> activeAccounts = AccountsDao.instance.findAll(combinedFilter);
        logger.info("executeTaskHybridAccounts: accounts length" + activeAccounts.size());
        for(Account account: activeAccounts) {
            try {
                Context.accountId.set(account.getId());
                consumeAccount.accept(account);
            } catch (Exception e) {
                String msgString = String.format("Error in executing task %s for account %d", taskName, account.getId());
                logger.error(msgString, e);
            }
        }

    }

    public void executeTaskForNonHybridAccounts(Consumer<Account> consumeAccount, String taskName) {

        Bson activeFilter = Filters.or(
                Filters.exists(Account.INACTIVE_STR, false),
                Filters.eq(Account.INACTIVE_STR, false)
        );

        Bson nonHybridAccountsFilter = Filters.or(
            Filters.exists(Account.HYBRID_TESTING_ENABLED, false),
            Filters.eq(Account.HYBRID_TESTING_ENABLED, false)
        );

        List<Account> activeAccounts = AccountsDao.instance.findAll(Filters.and(activeFilter, nonHybridAccountsFilter));
        for(Account account: activeAccounts) {
            if (inactiveAccountsSet.contains(account.getId())) {
                continue;
            }
            try {
                Context.accountId.set(account.getId());
                consumeAccount.accept(account);
            } catch (Exception e) {
                String msgString = String.format("Error in executing task %s for account %d", taskName, account.getId());
                logger.error(msgString, e);
            }
        }
    }
}
