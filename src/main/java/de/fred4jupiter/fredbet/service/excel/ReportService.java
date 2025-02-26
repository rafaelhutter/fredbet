package de.fred4jupiter.fredbet.service.excel;

import de.fred4jupiter.fredbet.domain.*;
import de.fred4jupiter.fredbet.props.FredbetConstants;
import de.fred4jupiter.fredbet.repository.*;
import de.fred4jupiter.fredbet.service.ranking.RankingService;
import de.fred4jupiter.fredbet.util.DateUtils;
import de.fred4jupiter.fredbet.util.MessageSourceUtil;
import de.fred4jupiter.fredbet.util.Validator;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReportService {

    private final ExcelExportService excelExportService;

    private final BetRepository betRepository;

    private final ExtraBetRepository extraBetRepository;

    private final MessageSourceUtil messageSourceUtil;

    private final MatchRepository matchRepository;

    private final RankingService rankingService;

    public ReportService(ExcelExportService excelExportService, BetRepository betRepository, ExtraBetRepository extraBetRepository,
                         MessageSourceUtil messageSourceUtil, MatchRepository matchRepository, RankingService rankingService) {
        this.excelExportService = excelExportService;
        this.betRepository = betRepository;
        this.extraBetRepository = extraBetRepository;
        this.messageSourceUtil = messageSourceUtil;
        this.matchRepository = matchRepository;
        this.rankingService = rankingService;
    }

    public byte[] exportBetsToExcel(final Locale locale) {
        List<Match> finalMatches = matchRepository.findByGroup(Group.FINAL);
        if (Validator.isNotEmpty(finalMatches)) {
            // there should be only one final match (ignoring if more)
            Match match = finalMatches.get(0);
            if (match.hasResultSet()) {
                return exportBetsToExcel(locale, true);
            }
        }

        return exportBetsToExcel(locale, false);
    }

    private byte[] exportBetsToExcel(final Locale locale, boolean withBets) {
        Sort sort = Sort.by(new Order(Direction.DESC, "points"), new Order(Direction.ASC, "userName"));
        final List<Bet> bets = this.betRepository.findAll(sort);

        return excelExportService.exportEntriesToExcel("Bets export", bets, new ListEntryCallback<>() {

            @Override
            public void addHeaderRow(List<String> header) {
                header.add(messageSourceUtil.getMessageFor("excel.export.username", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.team1", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.team2", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.date", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.resultTeam1", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.resultTeam2", locale));

                if (withBets) {
                    header.add(messageSourceUtil.getMessageFor("excel.export.bet1", locale));
                    header.add(messageSourceUtil.getMessageFor("excel.export.bet2", locale));
                }

                header.add(messageSourceUtil.getMessageFor("excel.export.joker", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.points", locale));
            }

            @Override
            public void addValueRow(Bet bet, List<String> row) {
                row.add(bet.getUserName());
                row.add(messageSourceUtil.getCountryName(bet.getMatch().getTeamOne().getCountry(), locale));
                row.add(messageSourceUtil.getCountryName(bet.getMatch().getTeamTwo().getCountry(), locale));
                row.add(DateUtils.formatByLocale(bet.getMatch().getKickOffDate(), locale));
                if (bet.getMatch().hasResultSet()) {
                    row.add("" + bet.getMatch().getGoalsTeamOne());
                    row.add("" + bet.getMatch().getGoalsTeamTwo());
                } else {
                    row.add("");
                    row.add("");
                }

                if (withBets) {
                    row.add("" + bet.getGoalsTeamOne());
                    row.add("" + bet.getGoalsTeamTwo());
                }

                row.add(jokerYesNoLocalized(bet.isJoker(), locale));
                row.add("" + bet.getPoints());
            }
        });
    }

    public byte[] exportExtraBetsToExcel(Locale locale) {
        final List<ExtraBet> extraBets = this.extraBetRepository.findAll(Sort.by(new Order(Direction.ASC, "userName")));

        return excelExportService.exportEntriesToExcel("Extra-Bets export", extraBets, new ListEntryCallback<>() {

            @Override
            public void addHeaderRow(List<String> header) {
                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.username", locale));

                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.finalWinner", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.pointsOne", locale));

                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.semiFinalWinner", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.pointsTwo", locale));

                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.thirdFinalWinner", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.extrabet.pointsThree", locale));
            }

            @Override
            public void addValueRow(ExtraBet extraBet, List<String> row) {
                row.add(extraBet.getUserName());
                row.add(messageSourceUtil.getCountryName(extraBet.getFinalWinner(), locale));
                row.add("" + extraBet.getPointsOne());
                row.add(messageSourceUtil.getCountryName(extraBet.getSemiFinalWinner(), locale));
                row.add("" + extraBet.getPointsTwo());
                row.add(messageSourceUtil.getCountryName(extraBet.getThirdFinalWinner(), locale));
                row.add("" + extraBet.getPointsThree());
            }
        });
    }

    private String jokerYesNoLocalized(boolean withJoker, Locale locale) {
        if (withJoker) {
            return messageSourceUtil.getMessageFor("excel.export.yes", locale);
        } else {
            return messageSourceUtil.getMessageFor("excel.export.no", locale);
        }
    }

    public MultiValuedMap<Integer, PointCountResult> reportPointsFrequency() {
        MultiValuedMap<Integer, PointCountResult> map = new ArrayListValuedHashMap<>();

        final List<PointCountResult> resultList = this.betRepository.countNumberOfPointsByUser();
        for (PointCountResult pointCountResult : resultList) {
            map.put(pointCountResult.getPoints(), pointCountResult);
        }

        return map;
    }

    public PointCourseContainer reportPointsCourse(String username, Locale locale) {
        List<PointsPerUser> pointsPerUsers = this.betRepository.queryPointsPerUser();
        ImmutablePair<String, String> pair = calculateMinMaxPointsUsernames(pointsPerUsers);

        PointCourseContainer pointCourseContainer = new PointCourseContainer();
        List<PointCourseResult> pointCourseResultList;
        if (pair != null) {
            pointCourseResultList = this.betRepository.queryPointsCourse(Arrays.asList(pair.getLeft(), username, pair.getRight()));
        } else {
            pointCourseResultList = this.betRepository.queryPointsCourse(Collections.singletonList(username));
        }

        pointCourseResultList.forEach(pointCourseResult -> {
            if (pointCourseResult.getMatch().hasResultSet()) {
                pointCourseContainer.add(pointCourseResult, messageSourceUtil, locale);
            }
        });
        return pointCourseContainer;
    }

    private ImmutablePair<String, String> calculateMinMaxPointsUsernames(List<PointsPerUser> pointsPerUsers) {
        PointsPerUser min = pointsPerUsers.stream()
                .filter(pointsPerUser -> !pointsPerUser.getUsername().equals(FredbetConstants.TECHNICAL_USERNAME))
                .min(Comparator.comparing(PointsPerUser::getPoints)).orElse(null);
        PointsPerUser max = pointsPerUsers.stream()
                .filter(pointsPerUser -> !pointsPerUser.getUsername().equals(FredbetConstants.TECHNICAL_USERNAME))
                .max(Comparator.comparing(PointsPerUser::getPoints)).orElse(null);
        return min != null && max != null ? ImmutablePair.of(min.getUsername(), max.getUsername()) : null;
    }

    public byte[] exportNumberOfPointsInBets(final Locale locale) {
        final List<PointCountResult> resultList = this.betRepository.countNumberOfPointsByUser();

        return excelExportService.exportEntriesToExcel("Bets point count export", resultList, new EntryCallback<>() {

            @Override
            public String[] getHeaderRow() {
                String userName = messageSourceUtil.getMessageFor("excel.export.username", locale);
                String points = messageSourceUtil.getMessageFor("excel.export.points", locale);
                String pointsCount = messageSourceUtil.getMessageFor("excel.export.pointsCount", locale);
                return new String[]{userName, points, pointsCount};
            }

            @Override
            public String[] getRowValues(PointCountResult pointCountResult) {
                return new String[]{pointCountResult.getUsername(), "" + pointCountResult.getPoints(),
                        "" + pointCountResult.getNumberOfPointsCount()};
            }
        });
    }

    public byte[] exportRankingToExcel(Locale locale) {
        List<UsernamePoints> rankings = rankingService.calculateCurrentRanking(RankingSelection.MIXED);

        return excelExportService.exportEntriesToExcel("Ranking Export", rankings, new ListEntryCallback<>() {

            @Override
            public void addHeaderRow(List<String> header) {
                header.add(messageSourceUtil.getMessageFor("excel.export.ranking.username", locale));
                header.add(messageSourceUtil.getMessageFor("excel.export.ranking.points", locale));
            }

            @Override
            public void addValueRow(UsernamePoints usernamePoints, List<String> rowValues) {
                rowValues.add(usernamePoints.getUserName());
                rowValues.add("" + usernamePoints.getTotalPoints());
            }
        });
    }
}
