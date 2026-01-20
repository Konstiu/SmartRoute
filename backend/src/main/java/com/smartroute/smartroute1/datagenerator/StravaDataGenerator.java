package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ApplicationUser;
import com.smartroute.smartroute1.entity.StravaAccount;
import com.smartroute.smartroute1.entity.Activity;
import com.smartroute.smartroute1.repository.StravaAccountRepository;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.repository.UserRepository;
import com.smartroute.smartroute1.service.FitnessScoreService;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Profile("generateData")
@DependsOn("userDataGenerator")
@Component
@AllArgsConstructor
public class StravaDataGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final int NUMBER_OF_ACTIVITIES_PER_USER = 10;

    private final StravaAccountRepository stravaAccountRepository;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final FitnessScoreService fitnessScoreService;
    private final InjuryDataGenerator injuryDataGenerator;


    private final String polyline = "iqkeHu{ecB@@@@FDABDA??A??AA?AA@???C?A?B"
            + "@??@A@????@@??AA?@A???BAB?AFP?DBLFLDHBBFRHNDPNZTh@Vp@Zh@Tp@Vl@\\"
            + "d@\\j@Xj@Pv@Xt@Xt@Xr@Zr@\\r@\\h@Tv@Zh@Zl@Xn@Xr@Tp@Vp@Vp@Xn@Vn@Zn@Vp@Z"
            + "n@Tj@Xj@Vj@Vl@Zl@Zr@Zp@Zp@Xr@Zn@Zp@Zn@Zp@Xr@Xl@Zr@Xn@Xp@Xp@Xn@^r@Zt@Zr@Z"
            + "v@Xt@\\t@\\r@\\r@\\v@Zr@\\v@\\v@Zt@\\v@Xt@\\r@Zt@Vp@\\r@\\n@Zl@Zp@^p@Zp@Xt@\\"
            + "r@Zr@Xn@Zr@Xt@Xv@^r@Zp@^p@Vv@Vt@^p@\\p@\\t@Zx@Xr@Xv@Zx@Zp@Zr@Zp@\\x@Zp@Zr@\\j@d"
            + "@\\j@Jh@Al@Ej@El@@l@Dl@Bj@Lj@Rf@^d@Zh@G`@k@\\u@Zs@Xs@Zu@\\w@Xs@Zw@\\u@Zu@Zw@\\u@Xs"
            + "@Zu@\\u@Vs@Zy@\\q@Zw@Zs@\\u@Zu@Zu@Zq@Xq@Zq@Zu@\\m@Vs@Vs@\\u@\\o@Xy@Zs@Vs@\\u@Zs@\\u@"
            + "Xw@Zu@Zu@Zu@Zs@Zw@\\s@Xs@Zu@\\w@Xw@\\u@Xs@^u@\\s@Zu@Zs@Xq@\\y@Xm@Xs@\\{@Zw@\\u@Zs@Xs"
            + "@Zu@\\u@Zw@Zu@Zq@Xy@Zs@\\u@\\w@Xq@Xs@\\s@Vs@Zu@\\s@Xq@Zs@Xs@\\k@Nc@Xk@Ti@\\q@Xs@`@Ud@`"
            + "@f@\\h@Rj@Nl@Dj@Hl@@l@?f@Aj@@j@Fn@Hn@Ll@Ll@Ph@Vj@Xh@^h@Vf@Xd@\\h@\\d@^f@\\d@b@d@f@`@b@d"
            + "@b@b@f@b@h@^l@^h@`@l@\\f@Xt@Vn@Np@_@b@e@`@e@Ze@Xe@Ze@^e@\\e@^i@\\e@Ze@\\g@\\i@\\g@Zi@\\"
            + "g@Ze@Zc@`@e@b@e@f@a@j@]p@]r@]p@[r@Wv@Yt@Sz@Ux@Ol@CJQ`AM|@M|@M~@M|@I~@Mz@GdAK|@I`AK`AI~@M~"
            + "@G|@K`AK`AUx@Wx@_@n@]n@a@j@c@f@c@`@g@`@e@`@e@Zi@Rk@NOD]Dk@Dm@Dm@Di@Dq@@o@Fk@Dk@Fk@Jo@Lm@Nk"
            + "@Pk@Rk@Rm@Ri@Ve@Tk@\\k@Xi@Ve@^g@Zg@Ze@Zg@Zi@Ze@^g@`@i@`@e@b@e@`@c@l@c@j@_@r@[v@Yv@Ux@Wt@U|@"
            + "M~@Oz@M|@Kp@ANG~@K~@K`AM`AMbAG`A?bA@~@D`AFbAFbAN~@Vr@T|@Tt@Vv@Xx@Tx@Pz@Tz@L~@J~@LbALz@L~@Hz@"
            + "^h@j@Lj@Lj@Lj@Lj@Hj@Ff@Hn@Fj@Jn@Fn@Hj@Hj@Lh@Jh@Nj@Z\\h@VZVT\\^f@b@b@d@b@`@b@h@b@h@f@b@b@b@d@"
            + "d@d@d@b@`@b@h@b@f@`@f@d@d@d@^d@d@`@`@b@h@`@d@f@`@d@b@b@j@b@b@d@d@b@b@b@f@f@b@`@b@b@b@b@d@b@b@"
            + "b@^d@b@`@h@Zn@Rx@L|@P`AP|@E~@K|@MbAM|@M~@M|@O~@Mz@I~@K`AOz@Kz@O|@Mz@M`AK|@O~@FbAh@Rp@Fj@Th@Th@R"
            + "d@b@`@j@`@d@h@j@b@l@`@n@^p@\\p@Zp@Zr@Zr@Zp@\\r@Zv@Xp@Zp@Zp@^t@Vt@Xv@Zr@Vp@Zr@Xv@Xv@Pz@L~@L~@J`A"
            + "J~@J~@J~@L`AJ`AJ~@H~@L|@J~@Pz@Nv@R|@Tv@Vx@Tv@\\v@b@l@`@j@d@d@d@f@f@Xj@Rj@Jl@Dl@Ch@Ch@?l@Ij@Bj@Ej"
            + "@Bl@Hh@Lj@Nj@Pd@\\f@Vb@`@f@Zf@`@d@^d@f@b@f@^n@Xp@Zv@V|@N`AL`AR`AH`AJ~@J|@JdAJ`AF|@H~@H`AJ~@H|@J"
            + "`AH~@JbAF~@J~@D|@FdA@~@B`AB~@B`AD~@BbA@`AB`AFbAB~@FbAD`AB`AB`AD~@@bAD`AH~@LbANx@Z|@^p@^n@`@p@\\h@b"
            + "@h@f@b@h@^f@`@f@^f@b@f@^f@b@b@f@`@l@`@j@`@l@`@j@^l@\\v@Zt@T|@Rr@Tz@P|@L~@L|@H~@@|@D`A@`A?bA?bA@`A?d"
            + "AAbA@`A@dA?FAx@AdACbAK~@O~@Q|@Kl@Mn@Or@Kh@Qv@O~@S|@Qz@U~@Wx@Q|@Wx@Qz@S~@ObAU`AOdAO~@OdAS~@S|@Wz@Uz@Q`"
            + "ASz@Qx@Q|@S~@O|@Qz@S|@Q~@S|@Qx@Q~@Sz@S|@Q~@Sx@e@Zi@JS?UGg@Qg@Wk@Yi@Si@Ug@Si@Wg@[IIYe@G}@B_A@eA@cABeABg"
            + "ABcAFcA@aADeABeABcAAeACgAAgAA_AEeAEaAGcAAcAIaAEcAEeAKaAKaAKy@MaAS{@OcAS{@U_AIaAIaAM}@KaAGaACaAEcAC_AEcAC"
            + "cAAaACcACcAEaAGaAGcACaAOeAQ}@Wy@a@q@a@e@g@a@c@c@c@e@a@c@g@a@c@i@a@g@a@k@]q@a@m@]s@[q@]s@ACWk@W}@Wy@Wy@Q"
            + "y@Uy@O}@M}@Uu@Sw@Qs@CcAD_AK}@Uw@[u@Sw@O}@QcAMcAS{@Q}@M_AEcAIeAK_AI_AI_AMcAI_AO{@O_AO_AG}@O}@Q{@M}@"
            + "S{@QcAQy@Sy@S_AOw@Q}@Q}@Qw@S}@Sw@[w@Yu@Uw@Yu@Qu@Ww@Ww@M_AKw@Ou@Qu@Wy@Uw@Uu@K{@O}@Ww@Sw@QaAO{@Io@G"
            + "e@Kk@K{@O{@K}@O{@OaAM_AI_AK}@Ws@Ws@[s@Yq@]k@[g@]k@c@g@c@g@i@F_@h@_@h@a@f@_@l@c@d@a@h@a@f@_@j@_@l@"
            + "a@d@_@j@_@h@]l@_@h@a@h@g@`@a@f@_@l@]l@]j@_@h@_@j@_@h@_@d@c@f@e@b@c@Zg@Vg@Li@Jg@Rc@Pk@Rk@Di@Gg@@s@"
            + "Ig@Ig@Ai@Ai@Gs@]k@Oi@Oi@Ik@Ie@Ik@Gm@Gg@Gg@Kg@Gk@Ii@Ae@Ak@Ei@Oi@Ug@Gi@Ci@Ka@m@H_AF}@Fy@F{@D}@F_AF}"
            + "@FaAK{@@cAF}@F}@B_AK{@Ws@[w@Wq@[w@Wu@e@i@[o@Qw@[s@[s@Ww@[_@W\\c@h@g@f@_@^a@j@e@d@a@d@_@f@e@^e@^g"
            + "@^g@Vk@Xe@\\g@^c@`@c@\\k@\\g@Jk@Tg@Ng@Re@Lk@Rg@Pi@Tg@`@e@^_@Z_@h@_@d@e@\\e@^e@\\a@d@c@`@g@\\_"
            + "@^a@d@g@^g@Ve@Ne@Rg@Tc@Vi@Nk@Hm@Y?Y?AA_AC}@AcAM_AI}@Ak@Ii@Os@O}@M}@O}@GaAK{@Q{@Uy@Ww@Us@Uq@Ys@Ww@"
            + "Uo@Qu@Su@[s@Uu@Qy@Sw@Ws@Wq@]u@Wu@Ly@^k@`@i@\\m@b@k@^i@^i@`@g@f@]j@Yb@]f@]f@Uf@Sj@Qf@Md@M`@Ul@Wd@Wj@"
            + "Sh@Gd@Ah@Gh@Ef@Qj@Wl@Ih@Nf@Dh@@j@A^D`@BXBVHb@Ab@Q`@a@b@c@^c@`@i@`@i@\\i@Zs@Vu@Pw@Xq@Vu@Zm@Zm@b@g@p@Sh@"
            + "Uj@Sb@Wb@a@`@_@b@c@\\c@Z_@d@_@b@_@b@a@`@_@d@[^]d@]b@Y`@Yb@]h@Qb@Eh@Sh@Af@OXS\\]Xq@Xo@Zq@Vq@Ts@Tu@R{@J"
            + "w@J{@B_AF{@Rw@D}@F{@D{@CaAA}@C}@?y@E}@A}@@_AC{@Gq@a@@g@Fe@La@Rg@Lw@Ie@o@Uq@[o@]o@Yk@]m@Yo@Uq@Ym@_@m@Yu@W"
            + "o@]m@Ys@Wm@Yk@Wo@Uu@Ys@Wq@[m@Uo@Wq@[k@Yk@Wm@Uo@Sq@[m@Yq@E_AVs@\\m@Vo@Vq@Ts@Ls@Ps@@_AHs@Tq@Vg@Tm@Vq@Tm@Zm"
            + "@Rc@L[Re@Pc@JI@IPg@FMFONa@Tc@J[Pc@R_@Tc@Na@Pa@Pc@N_@Pa@Xm@Vq@Xk@Vs@Zs@Xo@Xq@Xo@Vs@Tk@Zq@Xs@Zo@Xm@Vq@Zq@V"
            + "u@Vq@Zi@Vm@Xo@Vs@Xo@To@Zq@Zo@Vm@Te@N_@Pa@Nc@R_@Nc@Re@Ra@Pe@Pc@Ra@Na@Re@Pc@P[Pg@P_@P_@P_@Ne@Ra@Rc@Pc@Rc@P"
            + "_@Ni@P]Re@Vk@Vo@Zq@Vm@Vs@Xo@Vs@Xs@Vi@Tk@Vi@Zq@Xi@Rk@Tm@Tm@Xk@Xo@Vo@Tq@Vi@Ti@Vi@Xq@Tk@Vk@Rk@Lq@HOL[R[Ra@L"
            + "]Pg@N[Lk@Im@Si@a@i@c@]c@_@c@a@_@]a@[a@[c@[a@[c@Yc@Ye@Wc@Ge@Bc@Re@Re@Jg@C_@MYO[][e@a@g@]c@]c@a@e@c@[i@Qe@"
            + "Q_@]]a@AYb@`@f@Xb@Rf@Vd@\\^f@`@f@VZR\\N\\VXXZ`@Rj@Fj@If@Wf@Qj@Eh@Jh@Rf@Z`@\\b@Xd@`@b@Zd@`@f@^b@\\b@\\b@\\"
            + "b@\\^`@\\WRe@Re@Vo@Zq@Vq@Xm@Pa@Pe@Rc@Te@P_@Pe@Re@La@T_@Nc@Re@Nc@Ra@Tc@P_@Pc@Ra@Pc@La@Te@N]Re@Re@Pa@Pa@N_"
            + "@Tg@Re@Pe@Na@Rg@P_@Rc@Na@Rc@R_@Ne@Pg@Rc@N]P_@J[Pc@TYNg@Pa@Hc@V_@P_@Ra@Jg@Va@Xq@Xq@Vs@Ae@AC@ABA`@WXo@Zq@V"
            + "m@To@Ze@Tq@Vo@Vg@Na@@C@AJWP]Tc@P_@Pg@Na@Ra@Pa@Lc@Ra@Ti@Rc@Pa@X]Li@Pc@Pc@Pa@Rc@P_@Pe@Pa@Rc@N_@Re@Pa@R_@Nc@"
            + "V_@La@Tc@Ja@T_@R]P_@Lk@Na@Ra@Na@Te@Pe@Ta@P]Pe@Na@Vc@Hg@Bg@Js@^k@LCZPBJB^Kp@W\\c@^]^Wl@Sb@Qb@M\\KZYb@Qf@S`@"
            + "Ub@Qb@Qf@K\\ORIVQb@S\\Qf@Q`@Sf@Yl@Yl@Yr@Yf@Qd@Sf@O\\Sd@U`@Q`@Ob@Q^S`@Uf@O\\S`@Od@Sd@Qh@SZUj@Ob@Q^Ub@Sd@O\\"
            + "Sf@Ob@Uf@Sb@O^M^Ub@Sb@Q`@Q^Qb@S`@Ob@S\\Mf@Wd@M`@U`@O^Ud@Q`@S^M\\Sl@M`@Sb@SZQf@OVS^MXM`@Qb@Qb@Q`@M^Mb@U\\Q^"
            + "Sb@Sd@[p@Yl@Wj@Wj@Wl@K`@Ub@Q`@Qb@Qh@]p@Yp@[n@[t@Yl@Sj@Sb@Q\\M\\Ud@Mf@Wh@]p@[r@]t@[r@[l@[r@Qn@Qh@N`@^`@b@Xj@"
            + "Vh@Lb@H\\@\\@\\D\\CZF^B\\HZ@\\J\\B?h@[f@Wp@O|@Cz@Hz@Vt@^l@Zf@T\\Nd@Tz@Pv@L|@Fv@?n@?v@Az@Ez@Gp@Gh@Mn@Md@Yh@Wp@"
            + "[n@[l@[l@[l@[r@[n@Wj@Wt@U~@Sp@O\\Ih@Ul@Ut@Qx@Sv@Sx@Sx@Mr@Mf@Mb@Md@Oh@Mf@Ud@Ib@Qd@Wn@Wn@Yl@Yh@_@h@S\\S`@_@h@a"
            + "@f@a@f@a@\\c@b@c@b@a@ZWXWZ_@\\e@Xi@Pg@Ti@Vg@Pi@Tg@Re@Na@Lc@Ji@Le@F[H_@Bg@Hg@Bi@Ba@@_@@e@A_@A]AYGe@Ig@Ke@T[VW"
            + "VQZWVYVWX]`@c@`@c@f@Wb@QXc@`@g@^a@Z[RYPWT]Tc@`@_@f@Mv@BbALx@Fx@Hf@Bl@Hf@Dj@Fv@Fz@H~@@n@N`AFbANv@h@F`@L^RZNh@"
            + "Nd@Tf@Xf@R^Fd@Nh@Rf@NVJTJZJZHd@f@H|@C`AA|@E|@Cr@Al@Cn@E~@C`AAz@A~@?l@?h@Al@Ah@Cn@@r@Jv@`@^j@N`@LTJh@Jf@Ff@@`"
            + "@DX@\\H\\HZL\\JZL\\L`@Pd@\\`@^`@ZZ^VTTXXZZN\\R\\Xb@`@b@ZDz@Sv@Ov@Ov@M~@E|@Mv@Gp@V\\V^VPVZV\\VXb@f@b@d@\\\\PZT"
            + "ZZTXXXX`@^b@^\\VX\\RXVZX\\ZVVXXXVVZ\\XTZ`@`@^`@`@`@d@`@Z`@`@X\\VZXVVXVTNd@Ln@Lh@Jh@Hl@Fl@Dj@Ef@If@Ij@Kl@Gl@Gp"
            + "@Oz@Oz@Iz@Mx@Kz@Mr@Mt@Gr@Ot@I|@Mv@Ir@Mx@Mz@Kv@Gx@Mt@Qt@Ov@Gp@Kp@Iz@Ir@Kr@Mz@Ov@Kr@Cr@St@a@b@]j@a@j@[f@_@h@Y\\["
            + "j@_@f@_@h@[d@Yd@[f@a@f@_@h@a@j@_@f@a@j@c@h@_@l@a@h@]n@g@Vc@GIAk@Ok@Mm@Kk@Sm@Io@Am@Im@Im@Mk@Io@Mk@Mo@Mm@Mm@Qi@KYAA?";


    @PostConstruct
    public void generateAccounts() {
        List<ApplicationUser> userList = userRepository.findAll();
        if (!stravaAccountRepository.findAll().isEmpty()) {
            LOGGER.info("Accounts already generated");
        } else {
            LOGGER.info("generating {} Strava account entries", userList.size());

            for (ApplicationUser user : userList) {
                StravaAccount acc = new StravaAccount();
                acc.setUser(user);
                acc.setScopes("read,activity:read_all,profile:read_all");
                acc.setConnectedAt(Instant.now());
                acc.setAthleteId(user.getId());
                // acc.setAccessToken("DummyAccessToken" + user.getId());
                // acc.setRefreshToken("DummyRefreshToken" + user.getId());
                // acc.setExpiresAt(Instant.now().plusSeconds(300));
                stravaAccountRepository.save(acc);
                LOGGER.debug("saving account for user {} ", user.getId());
            }
        }

        generateActivities();
    }

    private void generateActivities() {
        List<ApplicationUser> userList = userRepository.findAll();
        if (!activityRepository.findAll().isEmpty()) {
            LOGGER.info("Activities already generated");
        } else {
            long id = 0;
            for (int i = 0; i < userList.size(); i++) {
                switch (i) {
                    case 1 -> {
                        // User 1: Beginner - irregular 0-3x per week, 2-6 km, moderate-slow pace
                        generateBeginnerActivities(userList.get(i));
                    }
                    case 2 -> {
                        // User 2: Advanced - 1-3x per week, 5-15 km, moderate pace
                        generateAdvancedActivities(userList.get(i));
                    }
                    case 3 -> {
                        // User 3: Pro - 3-5x per week, 8-28 km, moderate-high pace
                        generateProActivities(userList.get(i));
                    }
                    case 4 -> {
                        // User 4 Exhausted pro-athlete marathon run yesterday
                        generateExhaustedActivities(userList.get(i));
                    }
                    case 5 -> {
                        // User 5: Injury-aware Beginner (full year with injury gaps)
                        List<InjuryPeriod> beginnerInjuries = injuryDataGenerator.getInjuryAwareBeginnerPeriods();
                        generateActivitiesForPeriod(
                                userList.get(i),
                                365, // full year
                                beginnerInjuries,
                                0.35, // ~2-3x per week
                                3.0f, 7.0f, // 3-7km
                                5.5f, 1.5f, // 5:30-7:00 min/km
                                145f, 15f   // 145-160 bpm
                        );
                    }
                    case 6 -> {
                        // User 6: Injury-aware Advanced (full year with injury gaps)
                        List<InjuryPeriod> advancedInjuries = injuryDataGenerator.getInjuryAwareAdvancedPeriods();
                        generateActivitiesForPeriod(
                                userList.get(i),
                                365,
                                advancedInjuries,
                                0.5, // ~3-4x per week
                                8.0f, 16.0f, // 8-16km
                                4.5f, 1.0f, // 4:30-5:30 min/km
                                150f, 15f   // 150-165 bpm
                        );
                    }
                    case 7 -> {
                        // User 7: Elite Runner with ONE significant injury (to see ATL impact)
                        List<InjuryPeriod> eliteInjuries = injuryDataGenerator.getEliteWithOneInjuryPeriods();
                        generateActivitiesForPeriod(
                                userList.get(i),
                                365,
                                eliteInjuries,
                                0.85, // ~6x per week
                                12.0f, 35.0f, // 12-35km
                                3.2f, 0.8f, // 3:12-4:00 min/km
                                160f, 15f   // 160-175 bpm
                        );
                    }
                    default -> {
                        // Random activities for other users
                        for (int j = 0; j < NUMBER_OF_ACTIVITIES_PER_USER; j++) {
                            Activity sa = new Activity();
                            sa.setName("Activity " + j);
                            float distance = (float) (1 + Math.random() * 25) * 1000;
                            float avgSpeed = (float) (1000 / ((2.5 + Math.random() * 7.5) * 60));
                            int movingTime = (int) (distance / avgSpeed);
                            float maxSpeed = (float) Math.min(avgSpeed * 1.25, 1000 / (6 + Math.random() * 24 * 60));
                            float totalElevationGain = (float) (Math.random() * .1 * distance);

                            sa.setDistance(distance);
                            sa.setAverageSpeed(avgSpeed);
                            sa.setMovingTime(movingTime);
                            sa.setMaxSpeed(maxSpeed);
                            sa.setElapsedTime(sa.getMovingTime() + (int) (Math.random() * 600));
                            sa.setTotalElevationGain(totalElevationGain);
                            sa.setType("Run");
                            sa.setSportType("Run");
                            sa.setStartDate(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)));
                            sa.setStartDateLocal(Instant.now().minusSeconds((long) (Math.random() * 30 * 24 * 3600)));

                            float averageHeartrate = (float) (120 + Math.random() * 60);
                            sa.setAverageHeartrate(averageHeartrate);
                            float maxHeartrate = (float) Math.min(averageHeartrate * 1.1, (140 + Math.random() * 60));
                            sa.setMaxHeartrate(maxHeartrate);

                            float averageWatts = (float) (120 + Math.random() * 230);
                            sa.setAverageWatts(averageWatts);
                            sa.setKilojoules(averageWatts * movingTime / 1000);
                            sa.setSummaryPolyline(polyline.substring(0, (int) (polyline.length() * ((distance / 1000.0) / 42.195))));
                            sa.setUser(userList.get(i));

                            Integer sessionLoad = fitnessScoreService.calculateSessionLoad(
                                    distance / 1000,
                                    movingTime / 60,
                                    totalElevationGain);
                            sa.setSessionLoad(sessionLoad);
                            activityRepository.save(sa);
                        }
                    }
                }
            }

            for (ApplicationUser user : userList) {
                LOGGER.debug("generating activities for user {}", user.getEmail());
            }
        }
    }

    private void generateActivitiesForPeriod(ApplicationUser user, int daysBack, List<InjuryPeriod> injuryPeriods,
             double activityFrequency, float minDistance, float maxDistance,
             float basePace, float paceVariation, float baseHr, float hrVariation) {
        LocalDate today = LocalDate.now();
        int activitiesGenerated = 0;

        for (int dayOffset = 1; dayOffset <= daysBack; dayOffset++) {
            LocalDate activityDate = today.minusDays(dayOffset);

            // Skip if injured on this date
            if (isInjuredOnDate(activityDate, injuryPeriods)) {
                continue;
            }

            // Randomly decide if activity happens this day
            if (Math.random() > activityFrequency) {
                continue;
            }

            // Generate the activity
            float distanceKm = minDistance + (float) (Math.random() * (maxDistance - minDistance));
            Activity activity = createActivity(
                    user,
                    activityDate,
                    distanceKm,
                    basePace,
                    paceVariation,
                    baseHr,
                    hrVariation,
                    generateActivityName(activitiesGenerated, distanceKm)
            );

            activityRepository.save(activity);
            activitiesGenerated++;
        }

        LOGGER.info("Generated {} activities for user {} over {} days",
                activitiesGenerated, user.getEmail(), daysBack);
    }

    private boolean isInjuredOnDate(LocalDate date, List<InjuryPeriod> injuryPeriods) {
        for (InjuryPeriod period : injuryPeriods) {
            if (period.contains(date)) {
                return true;
            }
        }
        return false;
    }

    private String generateActivityName(int count, float distanceKm) {
        String[] prefixes = {"Morning", "Evening", "Long", "Easy", "Tempo", "Recovery",
            "Speed", "Hill", "Interval", "Endurance"};
        String prefix = prefixes[count % prefixes.length];

        if (distanceKm > 20) {
            return prefix + " Long Run";
        }
        if (distanceKm < 5) {
            return prefix + " Recovery";
        }
        return prefix + " Run";
    }

    private Activity createActivity(
            ApplicationUser user,
            LocalDate date,
            float distanceKm,
            float basePaceMinPerKm,
            float paceVariation,
            float baseHr,
            float hrVariation,
            String name
    ) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setUser(user);

        float distance = distanceKm * 1000;
        float avgSpeed = (float) (1000 / ((basePaceMinPerKm + Math.random() * paceVariation) * 60));
        int movingTime = (int) (distance / avgSpeed);

        activity.setDistance(distance);
        activity.setAverageSpeed(avgSpeed);
        activity.setMovingTime(movingTime);
        activity.setMaxSpeed(avgSpeed * 1.20f);
        activity.setElapsedTime(movingTime + (int) (Math.random() * 400));
        activity.setTotalElevationGain((float) (Math.random() * 100 + 20));
        activity.setType("Run");
        activity.setSportType("Run");

        Instant activityInstant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        activity.setStartDate(activityInstant);
        activity.setStartDateLocal(activityInstant);

        float avgHr = baseHr + (float) (Math.random() * hrVariation);
        activity.setAverageHeartrate(avgHr);
        activity.setMaxHeartrate(avgHr * 1.10f);

        float watts = (float) (200 + Math.random() * 80);
        activity.setAverageWatts(watts);
        activity.setKilojoules(watts * movingTime / 1000);

        activity.setSummaryPolyline(
                polyline.substring(0, (int) (polyline.length() * (distanceKm / 42.195)))
        );

        Integer sessionLoad = fitnessScoreService.calculateSessionLoad(
                distanceKm,
                movingTime / 60,
                activity.getTotalElevationGain()
        );
        activity.setSessionLoad(sessionLoad);

        return activity;
    }

    private void generateBeginnerActivities(ApplicationUser user) {
        // Beginner: 18 activities over 2 months, irregular (0-3x/week)
        // Days when running occurred: [3, 7, 14, 16, 21, 28, 30, 35, 42, 44, 49, 51,
        // 56, 58]
        int[] daysAgo = {3, 7, 14, 16, 21, 28, 30, 35, 42, 44, 49, 51, 56, 58};
        float[] distances = {2.5f, 3.2f, 2.8f, 4.1f, 3.5f, 2.3f, 5.2f, 3.8f, 4.5f, 3.1f, 5.8f, 4.2f, 3.6f, 6.0f};
        int[] sessionLoads = {25, 32, 28, 42, 35, 23, 55, 38, 47, 31, 62, 44, 36, 68}; // TRIMP-like values
        String[] names = {"Morning Jog", "Easy Run", "Park Run", "Evening Run", "Recovery Run", "Short Run",
            "Weekend Run", "Leisure Run", "Easy Pace", "Slow Run", "Comfortable Run", "Sunday Run", "Light Jog",
            "Relaxed Run"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000; // km to meters
            float avgSpeed = (float) (1000 / ((5.5 + Math.random() * 1.5) * 60)); // 5:30-7:00 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.15f;
            float totalElevationGain = (float) (Math.random() * 30 + 10); // 10-40m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 300 + 60));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (145 + Math.random() * 15); // 145-160 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.12f);

            float averageWatts = (float) (150 + Math.random() * 40);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline(polyline.substring(0, (int) (polyline.length() * ((distance / 1000.0) / 42.195))));
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }

    private void generateAdvancedActivities(ApplicationUser user) {
        // Advanced: 20 activities over 2 months (1-3x/week)
        int[] daysAgo = {1, 4, 7, 10, 14, 17, 21, 24, 28, 31, 35, 38, 42, 45, 49, 52, 56, 59};
        float[] distances = {7.2f, 10.5f, 6.8f, 12.3f, 8.5f, 10.0f, 14.2f, 9.1f, 11.8f, 7.5f, 13.5f, 8.8f, 10.2f,
            12.0f, 9.5f, 11.0f, 14.8f, 10.5f};
        int[] sessionLoads = {85, 125, 78, 148, 102, 118, 172, 108, 142, 88, 162, 105, 122, 145, 112, 132, 180, 125}; // TRIMP-like
        // values
        String[] names = {"Tempo Run", "Long Run", "Recovery Run", "Steady Pace", "Morning Run",
            "Hill Workout", "Weekend Long", "Easy Run", "Interval Training", "Base Run", "Endurance Run",
            "Recovery Jog", "Steady Run", "Long Distance", "Moderate Run", "Training Run", "Extended Run",
            "Cardio Session"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000;
            float avgSpeed = (float) (1000 / ((4.5 + Math.random() * 1.0) * 60)); // 4:30-5:30 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.20f;
            float totalElevationGain = (float) (Math.random() * 50 + 30); // 30-80m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 400 + 120));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (150 + Math.random() * 15); // 150-165 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.10f);

            float averageWatts = (float) (180 + Math.random() * 50);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline(polyline.substring(0, (int) (polyline.length() * ((distance / 1000.0) / 42.195))));
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }

    private void generateProActivities(ApplicationUser user) {
        // Pro: 25 activities over 2 months (3-5x/week)
        int[] daysAgo = {1, 3, 5, 8, 10, 12, 15, 17, 19, 22, 24, 26, 29, 31, 33, 36, 38, 40, 43, 45, 47, 50, 52, 54,
            57};
        float[] distances = {12.5f, 18.2f, 10.0f, 22.5f, 15.3f, 20.0f, 25.8f, 12.0f, 16.5f,
            14.2f, 21.0f, 11.5f, 27.5f, 13.8f, 19.5f, 16.0f, 23.2f, 14.5f,
            20.5f, 17.8f, 24.0f, 15.5f, 21.8f, 18.5f, 26.5f};
        int[] sessionLoads = {152, 225, 118, 278, 188, 245, 320, 145, 202,
            172, 258, 138, 342, 168, 238, 195, 288, 178,
            252, 218, 298, 190, 268, 228, 328}; // TRIMP-like values for pro athletes
        String[] names = {"Speed Workout", "Long Run", "Recovery Run", "Race Pace", "Tempo Run",
            "Endurance", "Marathon Prep", "Easy Miles", "Interval Session", "Base Building",
            "Half Marathon Pace", "Active Recovery", "Long Distance", "Hill Repeats", "Steady State",
            "Aerobic Run", "Threshold Run", "Easy Run", "Progressive Run", "Fartlek",
            "Distance Run", "Tempo Session", "Endurance Training", "Quality Run", "Volume Run"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000;
            float avgSpeed = (float) (1000 / ((3.5 + Math.random() * 1.0) * 60)); // 3:30-4:30 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.25f;
            float totalElevationGain = (float) (Math.random() * 80 + 40); // 40-120m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 500 + 180));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (155 + Math.random() * 15); // 155-170 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.08f);

            float averageWatts = (float) (220 + Math.random() * 60);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline(polyline.substring(0, (int) (polyline.length() * ((distance / 1000.0) / 42.195))));
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }
    }

    private void generateExhaustedActivities(ApplicationUser user) {
        int[] daysAgo = {1, 3, 5, 8, 10, 12, 15, 17, 19, 22, 24, 26, 29, 31, 33, 36, 38, 40, 43, 45, 47, 50, 52, 54,
            57};
        float[] distances = {42.195f, 18.2f, 10.0f, 22.5f, 15.3f, 20.0f, 25.8f, 12.0f, 16.5f,
            14.2f, 21.0f, 11.5f, 27.5f, 13.8f, 19.5f, 16.0f, 23.2f, 14.5f,
            20.5f, 17.8f, 24.0f, 15.5f, 21.8f, 18.5f, 26.5f};
        int[] sessionLoads = {
            1200,
            500, 118, 278, 188, 245, 320, 145, 202,
            172, 258, 138, 342, 168, 238, 195, 288, 178,
            252, 218, 298, 190, 268, 228, 328}; // TRIMP-like values for pro athletes
        String[] names = {"Marathon", "Long Run", "Recovery Run", "Race Pace", "Tempo Run",
            "Endurance", "Marathon Prep", "Easy Miles", "Interval Session", "Base Building",
            "Half Marathon Pace", "Active Recovery", "Long Distance", "Hill Repeats", "Steady State",
            "Aerobic Run", "Threshold Run", "Easy Run", "Progressive Run", "Fartlek",
            "Distance Run", "Tempo Session", "Endurance Training", "Quality Run", "Volume Run"};

        for (int i = 0; i < daysAgo.length; i++) {
            Activity activity = new Activity();
            activity.setName(names[i]);

            float distance = distances[i] * 1000;
            float avgSpeed = (float) (1000 / ((3.5 + Math.random() * 1.0) * 60)); // 3:30-4:30 min/km
            int movingTime = (int) (distance / avgSpeed);
            float maxSpeed = avgSpeed * 1.25f;
            float totalElevationGain = (float) (Math.random() * 80 + 40); // 40-120m

            activity.setDistance(distance);
            activity.setAverageSpeed(avgSpeed);
            activity.setMovingTime(movingTime);
            activity.setMaxSpeed(maxSpeed);
            activity.setElapsedTime(movingTime + (int) (Math.random() * 500 + 180));
            activity.setTotalElevationGain(totalElevationGain);
            activity.setType("Run");
            activity.setSportType("Run");

            long secondsAgo = (long) daysAgo[i] * 24 * 3600;
            activity.setStartDate(Instant.now().minusSeconds(secondsAgo));
            activity.setStartDateLocal(Instant.now().minusSeconds(secondsAgo));

            float averageHeartrate = (float) (155 + Math.random() * 15); // 155-170 bpm
            activity.setAverageHeartrate(averageHeartrate);
            activity.setMaxHeartrate(averageHeartrate * 1.08f);

            float averageWatts = (float) (220 + Math.random() * 60);
            activity.setAverageWatts(averageWatts);
            activity.setKilojoules(averageWatts * movingTime / 1000);
            activity.setSummaryPolyline(polyline.substring(0, (int) (polyline.length() * ((distance / 1000.0) / 42.195))));
            activity.setUser(user);
            activity.setSessionLoad(sessionLoads[i]);
            activityRepository.save(activity);
        }

    }
}
