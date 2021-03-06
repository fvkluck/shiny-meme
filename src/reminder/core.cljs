(ns reminder.core
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer [<p!]]
            [reagent.core :as r]
            [reagent.react-native :as rn]
            ["react-native" :as rn-js]
            [tick.core :as t]
            ["@notifee/react-native" :refer [TriggerType]]
            ["@notifee/react-native$default" :as n]
            ["@react-navigation/native" :refer [NavigationContainer]]
            ["@react-navigation/native-stack" :as nav-stack]))

(comment
  "Start a clojure (not clojurescript!) repl, with at least the stuff below in the dependencies,
   open the editor, connect to it and run the code below"
  (require '[clojure.edn :as edn]
           '[clojure.java.io :as io]
           '[cider.piggieback]
           '[krell.api :as krell]
           '[krell.repl])

  (def config (edn/read-string (slurp (io/file "build.edn"))))
  (apply cider.piggieback/cljs-repl (krell.repl/repl-env) (mapcat identity config)))

(def title-style {:font-size 50})

(def button-style { :align-items "center"
                   :justify-content "center"
                   :padding-vertical 12
                   :padding-horizontal 32
                   :border-radius 4
                   :elevation 3
                   :background-color "black"})

(def text-style { :font-size 16
                 :line-height 21
                 :font-weight "bold"
                 :letter-spacing 0.25
                 :color "white"})

(defonce state (r/atom {:cue-time "08:30" 
                        :reminder-time "17:00"
                        :goals {1 {:text "This is my first goal"}
                                2 {:text "This is my second goal"}}}))

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

(defn add-goal [state text]
  (let [id (new-id (:goals state))]
    (update state :goals conj [id {:text text}])))

(defn remove-goal [state g-id]
  (update state :goals dissoc g-id))

(defn update-goal-text [state g-id text]
  (assoc-in state [:goals g-id :text] text))

(defn mark-finished [state g-id]
  (update-in state [:goals g-id] assoc :finished true))

(defn finished? [goal]
  (:finished goal))

(defn send-notification
  "Not very clean interface yet. By default will send a notification 2 seconds from now. You can also input a trigger-time, or a time-str"
  [title body & {:keys [channel-name trigger-time time-str]
                                       :or {channel-name (:channel-name @state)
                                            trigger-time (t/>> (t/now) (t/new-duration 2 :seconds))}}]
  (let [tt (if time-str
             (->> time-str
                  (t/time)
                  (t/at (t/today))
                  (t/inst))
             trigger-time)]
    (go
      <p! (n/createTriggerNotification #js {"title" title
                                            "body" body
                                            "android" #js {"channelId" channel-name}}
                                       #js {"type" (.-TIMESTAMP TriggerType)
                                            "timestamp" (.getTime (js/Date. tt))}))))

(comment
  (send-notification "title" "body" :trigger-time (t/inst (t/at (t/today) (t/time "14:33"))))
  (send-notification "title" "body" :time-str "15:17"))

(defn send-notification-for-goal
  "TODO: separate calculation <> action"
  [goals]
  (let [body (->> goals
                  vals
                  (map :text)
                  (map (fn [g] [:p g]))
                  (str))]
    #_body
    (send-notification "Remember your goals" body)))

(defn cue-view [{:keys [navigation]}]
  (let [new-goal-text (r/atom "")]
    [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
     [rn/text {:style title-style} "Set your goals"]
     [rn/text "Please take a moment to consider what you want to achieve today."]
     (for [[g-id g] (:goals @state)]
       [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
        [rn/text-input {:key g-id :default-value (:text g)
                        :on-change-text #(swap! state update-goal-text g-id %)}]
        [(r/adapt-react-class rn-js/Pressable) {:on-press #(swap! state remove-goal g-id)
                                                :style button-style} [rn/text {:style text-style} "Remove"]]])
     [rn/text-input {:placeholder "my new goal" :on-change-text #(reset! new-goal-text %)
                     :on-end-editing #(do
                                        (when (seq @new-goal-text)
                                          (swap! state add-goal @new-goal-text))
                                        (.focus (-> % .-target))
                                        (.clear (-> % .-target)))}]
     [rn/button {:on-press #(send-notification-for-goal (:goals @state)) :title "Remind me tonight!"}]]))

(defn reminder-view [{:keys [navigation]}]
  [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rn/text {:style title-style} "Your plan"]
   [rn/text "For better or for worse this is what you had planned"]
   (for [[g-id g] (:goals @state)]
     [rn/view {:key g-id :flex-direction "row" :margin-bottom 10}
      [rn/text {:key g-id
                :style {:color (if (finished? g) "#009900" "#ff0000")}} (:text g)]
      [rn/button {:on-press #(swap! state mark-finished g-id) :title "Finished"}]])])

(defn home [{:keys [navigation]}]
  [rn/view {:style {:flex 1 :align-items "center" :justify-content "center"}}
   [rn/button {:on-press #(.navigate navigation "cue") :title "go to cue-view"}]
   [rn/button {:on-press #(.navigate navigation "reminder") :title "go to reminder-view"}]])

(def Stack (nav-stack/createNativeStackNavigator))

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
