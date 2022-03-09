(ns reminder.core
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [reagent.core :as r]
            [reagent.react-native :as rn]
            [tick.core :as t]
            ["@notifee/react-native" :refer [TriggerType]]
            ["@notifee/react-native$default" :as n]
            ["@react-navigation/native" :refer [NavigationContainer]]
            ["@react-navigation/native-stack" :as nav-stack]))

(comment
  (require '[clojure.edn :as edn]
           '[clojure.java.io :as io]
           '[cider.piggieback]
           '[krell.api :as krell]
           '[krell.repl])

  (def config (edn/read-string (slurp (io/file "build.edn"))))
  (apply cider.piggieback/cljs-repl (krell.repl/repl-env) (mapcat identity config)))

(def title-style {:font-size 50})

(def Stack (nav-stack/createNativeStackNavigator))

(defonce state (r/atom {:cue-time "08:30" 
                        :reminder-time "17:00"
                        :days [{:date "date"
                                :goals {1 {:text "This is my first goal"}
                                        2 {:text "This is my second goal"}}}]}))

(defn logger [x]
  (js/console.log "start logger")
  (doseq [k (js-keys x)]
    (js/console.log k) (js/console.log (aget x k)))
  x)

(defn new-id [goals]
  (if (empty? goals)
    0
    (->> goals
         keys
         (apply max)
         inc)))

(defn add-goal [day text]
  (let [id (new-id (:goals day))]
    (update day :goals conj [id {:text text}])))

(defn send-notification [title body & {:keys [channel-name trigger-time]
                                       :or {channel-name (:channel-name @state)
                                            trigger-time (t/>> (t/now) (t/new-duration 2 :seconds))}}]
  (go
    <p! (n/createTriggerNotification #js {"title" title
                                          "body" body
                                          "android" #js {"channelId" channel-name}}
                                     #js {"type" (.-TIMESTAMP TriggerType)
                                          "timestamp" (.getTime (js/Date. trigger-time))})))

(defn cue-view [{:keys [navigation]}]
  (let [day (r/cursor state [:days 0])
        new-goal-text (r/atom "")]
    [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
     [rn/text {:style title-style} "Set your goals"]
     [rn/text "Please take a moment to consider what you want to achieve today."]
     (for [[g-id g] (:goals @day)]
       [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
        [rn/text-input {:key g-id :default-value (:text g)
                        :on-change-text #(swap! day assoc-in [:goals g-id :text] %)}]
        [rn/button {:on-press #(swap! day update :goals dissoc g-id) :title "Remove"}]])
     [rn/text-input {:placeholder "my new goal" :on-change-text #(reset! new-goal-text %)
                     :on-end-editing #(do
                                        (swap! day add-goal @new-goal-text)
                                        (.focus (-> % .-target))
                                        (.clear (-> % .-target)))}]
     [rn/button {:on-press #(send-notification "Dit is een titel" "dit is een body") :title "Remind me tonight!"}]]))

(defn reminder-view [{:keys [navigation]}]
  (let [day (r/cursor state [:days 0])]
    [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
                   [rn/text {:style title-style} "Your plan"]
                   [rn/text "For better or for worse this is what you had planned"]
                   (for [[g-id g] (:goals @day)]
                     [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
                      [rn/text {:key g-id
                                :style {:color (if (:finished g) "#009900" "#ff0000")}} (:text g)]
                      [rn/button {:on-press #(swap! day update-in [:goals g-id] assoc :finished true) :title "Finished"}]])]))

(defn home [{:keys [navigation]}]
  [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rn/button {:on-press #(.navigate navigation "cue") :title "go to cue-view"}]
   [rn/button {:on-press #(.navigate navigation "reminder") :title "go to reminder-view"}]])

(defn app []
  [:> NavigationContainer {}
   [:> (.-Navigator Stack) {:screen-options {:title "Welcome to Reminder"
                                             :header-style {:background-color "green" :height 100}
                                             :header-tint-color "#fff"
                                             :header-title-style {:font-size 30 :text-align "center"}}}
    [:> (.-Screen Stack) {:name "home" :component (r/reactify-component home)
                          :options {:title "Home Page"}}]
    [:> (.-Screen Stack) {:name "reminder" :component (r/reactify-component reminder-view)
                          :options {:title "This is your reminder"}}]
    [:> (.-Screen Stack) {:name "cue" :component (r/reactify-component cue-view)
                          :options {:title "This is your cue"}}]
    ]])

(defn ^:export -main  [& args]
  (go
    (let [c (<p! (n/createChannel #js {:id "reminder-channel" :name "channel for reminder app"}))]
      (swap! state assoc :channel-name c)))
  (r/as-element [app]))
