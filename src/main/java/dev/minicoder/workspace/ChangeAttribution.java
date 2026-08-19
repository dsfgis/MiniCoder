package dev.minicoder.workspace;

/**
 * 区分用户预有变化、Agent 新建/修改、重叠变化和无法确定的 Git 归属。
 *
 * @author Self David (dsfgis@gmail.com)
 */
public enum ChangeAttribution {
    PREEXISTING,
    AGENT_CREATED,
    AGENT_MODIFIED,
    OVERLAPS_PREEXISTING_CHANGE,
    UNKNOWN
}
